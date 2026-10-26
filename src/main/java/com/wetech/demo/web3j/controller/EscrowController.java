package com.wetech.demo.web3j.controller;

import com.wetech.demo.web3j.service.EscrowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.web3j.protocol.exceptions.TransactionException;

import java.io.IOException;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Local assignment API. Authentication is intentionally outside this exercise. */
@RestController
@RequestMapping("/api/escrow")
public class EscrowController {
    private final EscrowService escrowService;

    public EscrowController(EscrowService escrowService) {
        this.escrowService = escrowService;
    }

    @GetMapping("/backend")
    public Map<String, String> backend() throws Exception {
        return escrowService.backendInfo();
    }

    @PostMapping("/deploy")
    public Map<String, String> deploy() throws Exception {
        return escrowService.deploy();
    }

    @PostMapping("/load")
    public Map<String, String> load(
            @RequestParam("address") String address,
            @RequestParam("fromBlock") BigInteger fromBlock) throws Exception {
        return escrowService.load(address, fromBlock);
    }

    @GetMapping("/address")
    public Map<String, String> address() throws Exception {
        return escrowService.address();
    }

    @GetMapping("/orders")
    public List<Map<String, String>> orders() throws Exception {
        return escrowService.listOrders();
    }

    @GetMapping("/orders/{id}")
    public Map<String, String> order(@PathVariable("id") BigInteger id) throws Exception {
        return escrowService.getOrder(id);
    }

    @PostMapping("/orders/{id}/resolve")
    public Map<String, String> resolve(
            @PathVariable("id") BigInteger id,
            @RequestParam("sellerWins") boolean sellerWins) throws Exception {
        return escrowService.resolve(id, sellerWins);
    }

    // Return useful errors instead of hiding all causes behind a generic HTTP 500.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleError(Exception error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        Map<String, String> result = new LinkedHashMap<>();
        result.put("error", cause.getClass().getSimpleName());
        result.put("message", cause.getMessage() == null
                ? "Request failed; inspect the server log" : cause.getMessage());

        if (cause instanceof IllegalArgumentException
                || cause instanceof MethodArgumentTypeMismatchException) {
            status = HttpStatus.BAD_REQUEST;
        } else if (cause instanceof IllegalStateException) {
            status = HttpStatus.CONFLICT;
        } else if (cause instanceof EscrowService.FailedReceiptException) {
            status = HttpStatus.CONFLICT;
            var receipt = ((EscrowService.FailedReceiptException) cause).getReceipt();
            addReceipt(result, receipt);
        } else if (cause instanceof TransactionException) {
            status = HttpStatus.CONFLICT;
            TransactionException transactionError = (TransactionException) cause;
            transactionError.getTransactionHash().ifPresent(hash ->
                    result.put("transactionHash", hash));
            transactionError.getTransactionReceipt().ifPresent(receipt ->
                    addReceipt(result, receipt));
        } else if (cause instanceof IOException) {
            status = HttpStatus.BAD_GATEWAY;
        }

        return ResponseEntity.status(status).body(result);
    }

    private static void addReceipt(Map<String, String> result,
                                   org.web3j.protocol.core.methods.response.TransactionReceipt receipt) {
        if (receipt.getTransactionHash() != null) {
            result.put("transactionHash", receipt.getTransactionHash());
        }
        if (receipt.getBlockNumberRaw() != null) {
            result.put("blockNumber", receipt.getBlockNumber().toString());
        }
        if (receipt.getStatus() != null) {
            result.put("status", receipt.getStatus());
        }
        if (receipt.getGasUsedRaw() != null) {
            result.put("gasUsed", receipt.getGasUsed().toString());
        }
    }
}