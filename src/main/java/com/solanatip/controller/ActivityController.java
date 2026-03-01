package com.solanatip.controller;

import com.solanatip.entity.Creator;
import com.solanatip.entity.Tip;
import com.solanatip.entity.TipStatus;
import com.solanatip.repository.CreatorRepository;
import com.solanatip.repository.TipRepository;
import lombok.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class ActivityController {

    private final TipRepository tipRepository;
    private final CreatorRepository creatorRepository;

    @GetMapping
    public ResponseEntity<List<ActivityEvent>> getActivityFeed(@RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(limit, 50);
        List<ActivityEvent> events = new ArrayList<>();

        // Recent confirmed tips (global)
        List<Tip> recentTips = tipRepository.getRecentConfirmedTipsGlobal(PageRequest.of(0, safeLimit));
        for (Tip tip : recentTips) {
            String senderName = tip.getSenderDisplayName() != null
                    ? tip.getSenderDisplayName()
                    : truncateWallet(tip.getSenderWalletAddress());

            events.add(ActivityEvent.builder()
                    .type("tip")
                    .message(tip.getMessage())
                    .senderName(senderName)
                    .senderWallet(tip.getSenderWalletAddress())
                    .creatorUsername(tip.getCreator().getUsername())
                    .creatorDisplayName(tip.getCreator().getDisplayName())
                    .creatorAvatar(tip.getCreator().getAvatarUrl())
                    .amountSol(tip.getAmountSol())
                    .timestamp(tip.getCreatedAt())
                    .build());
        }

        // Recent creator signups (last 30 days)
        List<Creator> newCreators = creatorRepository.findRecentCreators(
                LocalDateTime.now().minusDays(30), PageRequest.of(0, 10));
        for (Creator creator : newCreators) {
            events.add(ActivityEvent.builder()
                    .type("new_creator")
                    .creatorUsername(creator.getUsername())
                    .creatorDisplayName(creator.getDisplayName())
                    .creatorAvatar(creator.getAvatarUrl())
                    .timestamp(creator.getCreatedAt())
                    .build());
        }

        // Milestones — creators who crossed SOL thresholds
        List<Object[]> topCreators = tipRepository.getCreatorTotals();
        BigDecimal[] thresholds = {
                new BigDecimal("1"), new BigDecimal("5"), new BigDecimal("10"),
                new BigDecimal("25"), new BigDecimal("50"), new BigDecimal("100")
        };
        for (Object[] row : topCreators) {
            Creator creator = (Creator) row[0];
            BigDecimal total = (BigDecimal) row[1];
            // Find highest crossed threshold
            BigDecimal milestone = null;
            for (int i = thresholds.length - 1; i >= 0; i--) {
                if (total.compareTo(thresholds[i]) >= 0) {
                    milestone = thresholds[i];
                    break;
                }
            }
            if (milestone != null) {
                events.add(ActivityEvent.builder()
                        .type("milestone")
                        .creatorUsername(creator.getUsername())
                        .creatorDisplayName(creator.getDisplayName())
                        .creatorAvatar(creator.getAvatarUrl())
                        .amountSol(milestone)
                        .timestamp(creator.getCreatedAt()) // approximate
                        .build());
            }
        }

        // Sort all by timestamp desc, limit
        events.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        List<ActivityEvent> limited = events.stream().limit(safeLimit).collect(Collectors.toList());

        return ResponseEntity.ok(limited);
    }

    private String truncateWallet(String wallet) {
        if (wallet == null || wallet.length() < 8) return wallet;
        return wallet.substring(0, 4) + "..." + wallet.substring(wallet.length() - 4);
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class ActivityEvent {
        private String type; // "tip", "new_creator", "milestone"
        private String message;
        private String senderName;
        private String senderWallet;
        private String creatorUsername;
        private String creatorDisplayName;
        private String creatorAvatar;
        private BigDecimal amountSol;
        private LocalDateTime timestamp;
    }
}
