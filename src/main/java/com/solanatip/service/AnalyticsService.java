package com.solanatip.service;

import com.solanatip.dto.AnalyticsDto;
import com.solanatip.entity.Creator;
import com.solanatip.entity.SubscriptionPlan;
import com.solanatip.entity.Tip;
import com.solanatip.entity.TipStatus;
import com.solanatip.exception.ApiExceptions;
import com.solanatip.repository.CreatorRepository;
import com.solanatip.repository.TipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TipRepository tipRepository;
    private final CreatorRepository creatorRepository;

    public AnalyticsDto.Overview getAnalytics(String username, String period) {
        Creator creator = creatorRepository.findByUsername(username)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Creator not found"));

        if (creator.getSubscriptionPlan() != SubscriptionPlan.PRO) {
            throw new ApiExceptions.ForbiddenException("Analytics is a Pro feature");
        }

        var creatorId = creator.getId();
        LocalDateTime since = resolveSince(period);

        // All-time stats
        BigDecimal totalSol = tipRepository.sumAmountByCreatorIdAndStatus(creatorId, TipStatus.CONFIRMED);
        long totalTips = tipRepository.countByCreatorIdAndStatus(creatorId, TipStatus.CONFIRMED);
        BigDecimal averageTip = totalTips > 0
                ? totalSol.divide(BigDecimal.valueOf(totalTips), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        long uniqueSupporters = tipRepository.countUniqueSupporters(creatorId);

        // Period stats
        BigDecimal periodSol = tipRepository.sumConfirmedSince(creatorId, since);
        long periodTips = tipRepository.countConfirmedSince(creatorId, since);

        // Daily revenue chart
        List<Object[]> rawRevenue = tipRepository.getDailyRevenue(creatorId, since);
        List<AnalyticsDto.DailyRevenue> revenueChart = rawRevenue.stream()
                .map(row -> AnalyticsDto.DailyRevenue.builder()
                        .date(((Date) row[0]).toLocalDate())
                        .amount((BigDecimal) row[1])
                        .count(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());

        // Top supporters (top 10, all time)
        List<Object[]> rawSupporters = tipRepository.getTopSupporters(creatorId, 10);
        List<AnalyticsDto.TopSupporter> topSupporters = rawSupporters.stream()
                .map(row -> AnalyticsDto.TopSupporter.builder()
                        .walletAddress((String) row[0])
                        .displayName((String) row[1])
                        .totalAmount((BigDecimal) row[2])
                        .tipCount(((Number) row[3]).longValue())
                        .build())
                .collect(Collectors.toList());

        // Recent tips (last 20)
        List<Tip> rawRecent = tipRepository.getRecentConfirmedTips(creatorId, PageRequest.of(0, 20));
        List<AnalyticsDto.RecentTip> recentTips = rawRecent.stream()
                .map(t -> AnalyticsDto.RecentTip.builder()
                        .senderWalletAddress(t.getSenderWalletAddress())
                        .senderDisplayName(t.getSenderDisplayName())
                        .amountSol(t.getAmountSol())
                        .message(t.getMessage())
                        .createdAt(t.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return AnalyticsDto.Overview.builder()
                .totalSol(totalSol)
                .totalTips(totalTips)
                .averageTip(averageTip)
                .uniqueSupporters(uniqueSupporters)
                .periodSol(periodSol)
                .periodTips(periodTips)
                .revenueChart(revenueChart)
                .topSupporters(topSupporters)
                .recentTips(recentTips)
                .build();
    }

    private LocalDateTime resolveSince(String period) {
        return switch (period) {
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            case "90d" -> LocalDateTime.now().minusDays(90);
            default -> LocalDateTime.of(2020, 1, 1, 0, 0); // "all" — effectively all time
        };
    }
}
