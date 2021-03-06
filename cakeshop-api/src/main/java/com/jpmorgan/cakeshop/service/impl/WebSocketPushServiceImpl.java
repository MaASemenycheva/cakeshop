package com.jpmorgan.cakeshop.service.impl;

import com.jpmorgan.cakeshop.db.MetricsBlockListener;
import com.jpmorgan.cakeshop.error.APIException;
import com.jpmorgan.cakeshop.model.APIData;
import com.jpmorgan.cakeshop.model.APIResponse;
import com.jpmorgan.cakeshop.model.Block;
import com.jpmorgan.cakeshop.model.Node;
import com.jpmorgan.cakeshop.service.*;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.List;
import java.util.Map;

/**
 *
 * @author Michael Kazansky
 */
@Component
public class WebSocketPushServiceImpl implements WebSocketPushService {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(WebSocketPushServiceImpl.class);

    private Integer openedSessions = 0;

    /**
     * Transaction ID -> # of subscribers
     */
    private final Map<String, Integer> transactionsMap = new LRUMap(500);

    @Autowired(required = false)
    private SimpMessagingTemplate template;

    @Autowired
    private GethHttpService geth;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private BlockService blockService;

    @Autowired
    private WebSocketAsyncPushService asyncPushService;

    @Autowired
    private MetricsBlockListener metricsBlockListener;

    // For tracking status changes
    private Node previousNodeStatus;

    // For tracking block changes
    private Block previousBlock;

    @Scheduled(fixedDelay = 1000)
    public void pushTxnPerMin() {

        if (openedSessions <= 0) {
            return;
        }

        template.convertAndSend(
                "/topic/metrics/txnPerMin",
                APIResponse.newSimpleResponse(metricsBlockListener.getTxnPerMin()));
    }

    @Scheduled(fixedDelay = 1000)
    public void pushBlockPerMin() {

        if (openedSessions <= 0) {
            return;
        }

        template.convertAndSend(
                "/topic/metrics/blocksPerMin",
                APIResponse.newSimpleResponse(metricsBlockListener.getBlockPerMin()));
    }

    @Override
    @Scheduled(fixedDelay = 5000)
    public void pushNodeStatus() throws APIException {
        if (openedSessions <= 0) {
            return;
        }

        if (!geth.isConnected()) {
            // send back a node-down response
            Node node = new Node();
            node.setStatus(NodeService.NODE_NOT_RUNNING_STATUS);
            template.convertAndSend(NODE_TOPIC,
                    new APIResponse().data(new APIData(node.getId(), "node", node)));
            return;
        }

        Node node = nodeService.get();

        if (previousNodeStatus != null && node.equals(previousNodeStatus)) {
            return; // status has not changed...
        }
        previousNodeStatus = node;

        APIResponse apiResponse = new APIResponse();
        apiResponse.setData(new APIData(node.getId(), "node", node));
        template.convertAndSend(NODE_TOPIC, apiResponse);
    }

    @Override
    @Scheduled(fixedDelay = 5000)
    public void pushLatestBlocks() throws APIException {
        if (openedSessions <= 0 || !geth.isConnected()) {
            return;
        }

        Block block = blockService.get(null, null, "latest");

        if (previousBlock != null && block.equals(previousBlock)) {
            return; // did not change
        }
        previousBlock = block;

        APIResponse apiResponse = new APIResponse();
        apiResponse.setData(block.toAPIData());
        template.convertAndSend(BLOCK_TOPIC, apiResponse); // push block info

        // Also try to push tx info if block contains one we are watching
        List<String> transactions = block.getTransactions();
        for (String transaction : transactions) {
            if (transactionsMap.containsKey(transaction)) {
                asyncPushService.pushTransactionAsync(transaction, template, null);
            }
        }
    }

    @Override
    @Scheduled(fixedDelay = 200)
    public void pushTransactions() throws APIException {
        if (openedSessions <= 0 || transactionsMap.isEmpty() || !geth.isConnected()) {
            return;
        }

        for (String transactionAddress : transactionsMap.keySet()) {
            asyncPushService.pushTransactionAsync(transactionAddress, template, transactionsMap);
        }
    }

    @EventListener
    public void onSessionConnect(SessionConnectEvent event) {
        openedSessions++;
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        if (openedSessions > 0) {
            openedSessions--;
            if (openedSessions <= 0 && !transactionsMap.isEmpty()) {
                transactionsMap.clear();
            }
        }
    }

    @EventListener
    public void onSessionUnsubscribe(SessionUnsubscribeEvent event) {
        String dest = StompHeaderAccessor.wrap(event.getMessage()).getSubscriptionId();

        if (StringUtils.isBlank(dest)) {
            return;
        }

        LOG.debug("Unsubscribed: " + dest);

        if (dest.startsWith(TRANSACTION_TOPIC)) {
            String transactionKey = dest.substring(dest.lastIndexOf("/") + 1);
            Integer subscribers;

            if (transactionsMap.containsKey(transactionKey)) {
                subscribers = transactionsMap.get(transactionKey);
                subscribers--;
                if (subscribers <= 0) {
                    transactionsMap.remove(transactionKey);
                } else {
                    transactionsMap.put(transactionKey, subscribers);
                }
            }
        }
    }

    @EventListener
    public void onSessionSubscribe(SessionSubscribeEvent event) {
        String dest = StompHeaderAccessor.wrap(event.getMessage()).getDestination();
        if (StringUtils.isBlank(dest)) {
            return;
        }

        LOG.debug("Subscribed: " + dest);

        if (dest.startsWith(TRANSACTION_TOPIC)) {
            String transactionKey = dest.substring(dest.lastIndexOf("/") + 1);
            if (transactionKey.contentEquals("all")) {
                return; // special topic
            }

            if (transactionsMap.containsKey(transactionKey)) {
                Integer subscribers = transactionsMap.get(transactionKey);
                transactionsMap.put(transactionKey, subscribers++);
            } else {
                transactionsMap.put(transactionKey, 1);
            }

            // send status as soon as there is a (new) subscription
            // this can cause the message to be sent multiple times if there are
            // multiple subscribers on this topic (once per subscriber)
            if (dest.startsWith(NODE_TOPIC)) {
                try {
                    previousNodeStatus = null; // force push
                    pushNodeStatus();
                } catch (APIException e) {
                }
            }
        }
    }
}
