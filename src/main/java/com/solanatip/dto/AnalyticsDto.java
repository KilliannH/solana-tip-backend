package com.solanatip.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AnalyticsDto {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Overview {
        private BigDecimal totalSol;
        private long totalTips;
        private BigDecimal averageTip;
        private long uniqueSupporters;
        private BigDecimal periodSol;
        private long periodTips;
        private List<DailyRevenue> revenueChart;
        private List<TopSupporter> topSupporters;
        private List<RecentTip> recentTips;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class DailyRevenue {
        private LocalDate date;
        private BigDecimal amount;
        private long count;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class TopSupporter {
        private String walletAddress;
        private String displayName;
        private BigDecimal totalAmount;
        private long tipCount;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class RecentTip {
        private String senderWalletAddress;
        private String senderDisplayName;
        private BigDecimal amountSol;
        private String message;
        private LocalDateTime createdAt;
    }
}
