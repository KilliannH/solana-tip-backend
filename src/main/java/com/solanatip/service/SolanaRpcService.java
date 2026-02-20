package com.solanatip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.solanatip.exception.ApiExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SolanaRpcService {

    private static final BigDecimal LAMPORTS_PER_SOL = new BigDecimal("1000000000");
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 3000;

    private final WebClient webClient;

    public SolanaRpcService(@Value("${solana.rpc.url}") String rpcUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(rpcUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Verify a tip transaction on the Solana blockchain.
     * Retries several times because devnet indexing can be slow.
     */
    public TransactionVerification verifyTransaction(String signature, String expectedRecipient, BigDecimal expectedAmount) {
        JsonNode result = fetchTransactionWithRetry(signature);

        try {
            // Check confirmation status
            JsonNode meta = result.get("meta");
            if (meta != null && meta.has("err") && !meta.get("err").isNull()) {
                throw new ApiExceptions.TransactionVerificationException("Transaction failed on-chain");
            }

            // Parse transfer details from instructions
            JsonNode instructions = result.at("/transaction/message/instructions");
            if (instructions == null || !instructions.isArray()) {
                throw new ApiExceptions.TransactionVerificationException("Could not parse transaction instructions");
            }

            for (JsonNode instruction : instructions) {
                JsonNode parsed = instruction.get("parsed");
                if (parsed == null) continue;

                String type = parsed.has("type") ? parsed.get("type").asText() : "";
                if ("transfer".equals(type)) {
                    JsonNode info = parsed.get("info");
                    if (info == null) continue;

                    String destination = info.get("destination").asText();
                    long lamports = info.get("lamports").asLong();
                    BigDecimal solAmount = new BigDecimal(lamports).divide(LAMPORTS_PER_SOL, 9, RoundingMode.HALF_UP);
                    String source = info.get("source").asText();

                    // Verify recipient and amount match
                    if (destination.equals(expectedRecipient) && solAmount.compareTo(expectedAmount) == 0) {
                        return new TransactionVerification(true, source, destination, solAmount);
                    }
                }
            }

            throw new ApiExceptions.TransactionVerificationException(
                    "Transaction does not match expected recipient or amount");

        } catch (ApiExceptions.TransactionVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error verifying transaction {}: {}", signature, e.getMessage());
            throw new ApiExceptions.TransactionVerificationException("Failed to verify transaction: " + e.getMessage());
        }
    }

    /**
     * Fetch transaction with retry logic — devnet can be slow to index.
     */
    private JsonNode fetchTransactionWithRetry(String signature) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                JsonNode result = callRpc("getTransaction", List.of(
                        signature,
                        Map.of(
                                "encoding", "jsonParsed",
                                "commitment", "confirmed",
                                "maxSupportedTransactionVersion", 0
                        )
                ));

                if (result != null && !result.isNull()) {
                    log.info("Transaction {} found on attempt {}", signature, attempt);
                    return result;
                }

                if (attempt < MAX_RETRIES) {
                    log.info("Transaction {} not found yet, retrying in {}ms (attempt {}/{})",
                            signature, RETRY_DELAY_MS, attempt, MAX_RETRIES);
                    Thread.sleep(RETRY_DELAY_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiExceptions.TransactionVerificationException("Verification interrupted");
            }
        }

        throw new ApiExceptions.TransactionVerificationException(
                "Transaction not found on-chain after " + MAX_RETRIES + " attempts: " + signature);
    }

    private JsonNode callRpc(String method, Object params) {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", method,
                "params", params
        );

        JsonNode response = webClient.post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response != null && response.has("error")) {
            log.error("Solana RPC error: {}", response.get("error"));
            throw new ApiExceptions.TransactionVerificationException(
                    "Solana RPC error: " + response.get("error").get("message").asText());
        }

        return response != null ? response.get("result") : null;
    }

    public record TransactionVerification(boolean confirmed, String sender, String recipient, BigDecimal amount) {}
}