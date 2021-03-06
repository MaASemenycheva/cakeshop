package com.jpmorgan.cakeshop.test;

import com.jpmorgan.cakeshop.error.APIException;
import com.jpmorgan.cakeshop.model.Web3DefaultResponseType;
import org.testng.annotations.Test;
import org.web3j.protocol.core.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class GethRpcTest extends BaseGethRpcTest {

    private final String expectedHash = "0xb89f8664868b71eee61daf6eb8f709e3800b77501172430cb3167cd6692a878e";

    @Test
    public void testExecWithParams() throws APIException {
        String method = "eth_getBlockByNumber";

        Map<String, Object> data = geth.executeGethCall(method, new Object[]{"latest", false});
        assertNotNull(data.get("hash"));

        data = geth.executeGethCall(method, new Object[]{"0x" + Long.toHexString(0), false});
        assertEquals(data.get("hash"), expectedHash);

        data = geth.executeGethCall("eth_getBlockByHash", new Object[]{expectedHash, false});
        assertEquals(data.get("hash"), expectedHash);
    }

    @Test
    public void testBatchExec() throws APIException {

        List<Request<?, Web3DefaultResponseType>> reqs = new ArrayList<>();
        reqs.add(geth.createHttpRequestType("eth_getBlockByNumber", new Object[]{"0x" + Long.toHexString(0), false}));
        reqs.add(geth.createHttpRequestType("eth_getBlockByNumber", new Object[]{"0x" + Long.toHexString(0), false}));

        List<Map<String, Object>> batchRes = geth.batchExecuteGethCall(reqs);

        assertNotNull(batchRes);
        assertEquals(batchRes.size(), 2);

        for (Map<String, Object> data : batchRes) {
            assertEquals(data.get("hash"), expectedHash);
        }

    }

}
