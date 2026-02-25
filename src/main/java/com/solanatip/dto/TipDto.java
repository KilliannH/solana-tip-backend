package com.solanatip.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class TipDto {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "Creator username is required")
        private String creatorUsername;

        @NotBlank(message = "Sender wallet address is required")
        private String senderWalletAddress;

        private String senderDisplayName;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Minimum tip is 0.01 SOL")
        private BigDecimal amountSol;

        @NotBlank(message = "Transaction signature is required")
        private String transactionSignature;

        @Size(max = 280, message = "Message must be 280 characters or less")
        private String message;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {
        private UUID id;
        private String creatorUsername;
        private String creatorDisplayName;
        private String senderWalletAddress;
        private String senderDisplayName;
        private BigDecimal amountSol;
        private String transactionSignature;
        private String status;
        private String message;
        private LocalDateTime createdAt;
    }
}
