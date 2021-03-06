package com.jpmorgan.cakeshop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jpmorgan.cakeshop.model.SolcResponse.GasEstimates;
import com.jpmorgan.cakeshop.service.ContractService.CodeType;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Map;

public class Contract {

    public static final String API_DATA_TYPE = "contract";

    /**
     * Ethereum address of contract
     */
    private String address;

    /**
     * Contract (class) name
     */
    private String name;

    /**
     * Contract owner (address)
     */
    private String owner;

    /**
     * Original source code (not yet supported)
     */
    private String code;

    /**
     * Original source code type (not yet supported)
     */
    private CodeType codeType;

    /**
     * Binary source code
     */
    private String binary;

    /**
     * Contract ABI (JSON string)
     */
    private String abi;

    /**
     * Date and time the contract was created
     */
    private Long createdDate;

    /**
     * Gas estimates for each method
     */
    private GasEstimates gasEstimates;

    /**
     * Hash of each method signaature (required for making EVM calls)
     */
    private Map<String, String> functionHashes;

    /**
     * Comma-separated public keys of in a private contract
     * This is not saved in the ContractRegistry, but is used in the front-end
     */
    private String privateFor = "";

    @JsonIgnore
    private ContractABI contractAbi;


    public Contract() {
    }

    public Contract(String address, String name, String abi, String code, CodeType codeType,
        String binary, Long createdDate, String privateFor) {
        this.address = address;
        this.name = name;
        this.code = code;
        this.codeType = codeType;
        this.binary = binary;
        this.createdDate = createdDate;
        this.privateFor = privateFor;
        setABI(abi);
    }


    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public CodeType getCodeType() {
        return codeType;
    }

    public void setCodeType(CodeType codeType) {
        this.codeType = codeType;
    }

    public String getBinary() {
        return binary;
    }

    public void setBinary(String binary) {
        this.binary = binary;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

    public String getABI() {
        return abi;
    }

    public void setABI(String abi) {
        this.abi = abi;
        this.contractAbi = ContractABI.fromJson(this.abi);
    }

    public void setABI(ContractABI contractABI) {
        this.abi = contractABI.toJson();
        this.contractAbi = contractABI;
    }


    public Long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Long createdDate) {
        this.createdDate = createdDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GasEstimates getGasEstimates() {
        return gasEstimates;
    }

    public void setGasEstimates(GasEstimates gasEstimates) {
        this.gasEstimates = gasEstimates;
    }

    public Map<String, String> getFunctionHashes() {
        return functionHashes;
    }

    public void setFunctionHashes(Map<String, String> functionHashes) {
        this.functionHashes = functionHashes;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * @return the contractAbi
     */
    public ContractABI getContractAbi() {
        return contractAbi;
    }

    public void setPrivateFor(String privateFor) {
        this.privateFor = privateFor;
    }

    public String getPrivateFor() {
        return privateFor;
    }

    public Contract shallowCopy() {
        return new Contract(address, name, abi, code, codeType, binary, createdDate, privateFor);
    }
}
