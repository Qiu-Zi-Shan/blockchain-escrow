package com.wetech.demo.web3j.service;

import com.wetech.demo.web3j.contracts.escrow.Escrow;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.ContractGasProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local classroom backend. Chain calls block until their results arrive. */
@Service
public class EscrowService {
    private static final String[] STATUS = {
            "Created", "Paid", "Completed", "Refunded", "Disputed"
    };
    private static final BigInteger STEP = BigInteger.TEN;

    private final Web3j web3j;
    private final Credentials credentials;
    private final ContractGasProvider gasProvider;

    private Escrow contract;
    private BigInteger deployBlock;

    public EscrowService(Web3j web3j, Credentials credentials,
                         ContractGasProvider gasProvider) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.gasProvider = gasProvider;
    }

    /** Inspect the actual backend signer without exposing its private key. */
    public Map<String, String> backendInfo() throws IOException {
        EthGetBalance response = web3j.ethGetBalance(
                credentials.getAddress(), DefaultBlockParameterName.LATEST).send();
        checkRpc(response);
        BigInteger balance = response.getBalance();
        return Map.of(
                "backendAddress", credentials.getAddress(),
                "balanceWei", balance.toString(),
                "balancePOT", new BigDecimal(balance).movePointLeft(18)
                        .stripTrailingZeros().toPlainString()
        );
    }

    /** Serialize state changes and Escrow writes in this application instance. */
    public synchronized Map<String, String> deploy() throws Exception {
        if (contract != null) {
            throw new IllegalStateException(
                    "An Escrow is already loaded. Reuse it instead of deploying again.");
        }

        Escrow deployed = Escrow.deploy(web3j, credentials, gasProvider,
                credentials.getAddress()).send();

        TransactionReceipt receipt = deployed.getTransactionReceipt()
                .orElseThrow(() -> new IllegalStateException(
                        "Deployment returned address " + deployed.getContractAddress()
                                + " but no receipt. Inspect the chain before retrying."));
        requireSuccessful(receipt);

        contract = deployed;
        deployBlock = receipt.getBlockNumber();

        Map<String, String> result = receiptMap(receipt);
        result.put("contractAddress", contract.getContractAddress());
        result.put("deployBlock", deployBlock.toString());
        result.put("arbiter", credentials.getAddress());
        return result;
    }

    public synchronized Map<String, String> load(String address,
                                                BigInteger fromBlock) throws Exception {
        if (address == null || !address.matches("0[xX][0-9a-fA-F]{40}")
                || address.substring(2).matches("0{40}")) {
            throw new IllegalArgumentException("address must be a non-zero Ethereum address");
        }
        requireUint256(fromBlock, "fromBlock");

        EthBlockNumber height = web3j.ethBlockNumber().send();
        checkRpc(height);
        if (fromBlock.compareTo(height.getBlockNumber()) > 0) {
            throw new IllegalArgumentException("fromBlock is greater than the latest block");
        }

        EthGetCode code = web3j.ethGetCode(address, DefaultBlockParameterName.LATEST).send();
        checkRpc(code);
        if (code.getCode() == null || code.getCode().matches("(?i)0x0*")) {
            throw new IllegalArgumentException("No contract code exists at this address");
        }

        Escrow loaded = Escrow.load(address, web3j, credentials, gasProvider);
        // Verify that the expected read functions can be decoded before replacing state.
        String arbiter = loaded.arbiter().send();
        loaded.orderCount().send();

        contract = loaded;
        deployBlock = fromBlock;

        Map<String, String> result = new LinkedHashMap<>();
        result.put("contractAddress", address);
        result.put("fromBlock", fromBlock.toString());
        result.put("arbiter", arbiter);
        result.put("backendAddress", credentials.getAddress());
        result.put("backendIsArbiter",
                Boolean.toString(arbiter.equalsIgnoreCase(credentials.getAddress())));
        return result;
    }

    public synchronized Map<String, String> address() throws Exception {
        requireContract();
        String arbiter = contract.arbiter().send();
        return Map.of(
                "contractAddress", contract.getContractAddress(),
                "arbiter", arbiter,
                "backendAddress", credentials.getAddress(),
                "deployBlock", deployBlock.toString(),
                "backendIsArbiter",
                Boolean.toString(arbiter.equalsIgnoreCase(credentials.getAddress()))
        );
    }

    public synchronized Map<String, String> getOrder(BigInteger id) throws Exception {
        requireContract();
        requireUint256(id, "id");
        Escrow.Order order = contract.getOrder(id).send();
        return Map.of(
                "id", id.toString(),
                "buyer", order.buyer,
                "seller", order.seller,
                "token", order.token,
                "amount", order.amount.toString(),
                "deadline", order.deadline.toString(),
                "status", statusName(order.status),
                "description", order.description
        );
    }

    /** Creation history only; current status is read through getOrder. */
    public synchronized List<Map<String, String>> listOrders() throws Exception {
        requireContract();
        EthBlockNumber height = web3j.ethBlockNumber().send();
        checkRpc(height);
        BigInteger latest = height.getBlockNumber();
        List<Log> logs = new ArrayList<>();

        for (BigInteger from = deployBlock; from.compareTo(latest) <= 0;
             from = from.add(STEP)) {
            BigInteger to = from.add(STEP).subtract(BigInteger.ONE).min(latest);
            EthFilter filter = new EthFilter(
                    DefaultBlockParameter.valueOf(from),
                    DefaultBlockParameter.valueOf(to),
                    contract.getContractAddress());
            filter.addSingleTopic(EventEncoder.encode(Escrow.ORDERCREATED_EVENT));

            EthLog response = web3j.ethGetLogs(filter).send();
            checkRpc(response);
            if (response.getLogs() == null) {
                throw new IOException("RPC returned no log result for blocks " + from + "-" + to);
            }
            for (EthLog.LogResult<?> result : response.getLogs()) {
                if (!(result.get() instanceof Log)) {
                    throw new IOException("RPC returned an unexpected log format");
                }
                logs.add((Log) result.get());
            }
        }

        logs.sort(Comparator.comparing(Log::getBlockNumber)
                .thenComparing(Log::getLogIndex));

        Map<String, Map<String, String>> unique = new LinkedHashMap<>();
        for (Log log : logs) {
            if (log.isRemoved()) continue;
            Escrow.OrderCreatedEventResponse event =
                    Escrow.getOrderCreatedEventFromLog(log);
            Map<String, String> row = Map.of(
                    "id", event.id.toString(),
                    "buyer", event.buyer,
                    "seller", event.seller,
                    "token", event.token,
                    "amount", event.amount.toString(),
                    "deadline", event.deadline.toString(),
                    "blockNumber", log.getBlockNumber().toString(),
                    "transactionHash", log.getTransactionHash()
            );
            unique.put(event.id.toString(), row);
        }
        return new ArrayList<>(unique.values());
    }

    public synchronized Map<String, String> resolve(BigInteger id,
                                                   boolean sellerWins) throws Exception {
        requireContract();
        requireUint256(id, "id");
        if (!contract.arbiter().send().equalsIgnoreCase(credentials.getAddress())) {
            throw new IllegalStateException("The backend account is not this Escrow's arbiter");
        }

        // Deliberately let the contract reject non-Disputed orders (assignment test).
        TransactionReceipt receipt = contract.resolve(id, sellerWins).send();
        requireSuccessful(receipt);

        Map<String, String> result = receiptMap(receipt);
        result.put("id", id.toString());
        result.put("sellerWins", Boolean.toString(sellerWins));
        result.put("orderStatus", sellerWins ? "Completed" : "Refunded");
        result.put("contractAddress", contract.getContractAddress());
        return result;
    }

    private void requireContract() {
        if (contract == null) {
            throw new IllegalStateException("Call /api/escrow/deploy or /api/escrow/load first");
        }
    }

    private static void requireUint256(BigInteger value, String name) {
        if (value == null || value.signum() < 0 || value.bitLength() > 256) {
            throw new IllegalArgumentException(name + " must be a uint256 integer");
        }
    }

    private static String statusName(BigInteger value) {
        if (value.signum() < 0 || value.compareTo(BigInteger.valueOf(STATUS.length)) >= 0) {
            return "Unknown(" + value + ")";
        }
        return STATUS[value.intValue()];
    }

    private static void checkRpc(Response<?> response) throws IOException {
        if (response.hasError()) {
            throw new IOException("RPC " + response.getError().getCode()
                    + ": " + response.getError().getMessage());
        }
    }

    private static void requireSuccessful(TransactionReceipt receipt) {
        if (!receipt.isStatusOK()) {
            throw new FailedReceiptException(receipt);
        }
    }

    static Map<String, String> receiptMap(TransactionReceipt receipt) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("transactionHash", receipt.getTransactionHash());
        result.put("blockNumber", receipt.getBlockNumber().toString());
        result.put("gasUsed", receipt.getGasUsed().toString());
        result.put("status", receipt.getStatus());
        return result;
    }

    public static class FailedReceiptException extends RuntimeException {
        private final TransactionReceipt receipt;

        public FailedReceiptException(TransactionReceipt receipt) {
            super("Transaction reverted: " + receipt.getTransactionHash());
            this.receipt = receipt;
        }

        public TransactionReceipt getReceipt() {
            return receipt;
        }
    }
}