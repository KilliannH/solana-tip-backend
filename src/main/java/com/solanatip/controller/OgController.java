package com.solanatip.controller;

import com.solanatip.entity.SubscriptionPlan;
import com.solanatip.entity.TipStatus;
import com.solanatip.repository.CreatorRepository;
import com.solanatip.repository.TipRepository;
import com.solanatip.service.OgImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@Slf4j
public class OgController {

    private final CreatorRepository creatorRepository;
    private final TipRepository tipRepository;
    private final OgImageService ogImageService;

    @Value("${app.base-url:https://solana-tip.com}")
    private String baseUrl;

    /**
     * Dynamic OG image for a creator (1200x630 PNG).
     */
    @GetMapping("/api/v1/creators/{username}/og-image")
    public ResponseEntity<byte[]> getOgImage(@PathVariable String username) {
        if (creatorRepository.findByUsername(username).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] imageBytes = ogImageService.generateOgImage(username);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                    .body(imageBytes);
        } catch (Exception e) {
            log.error("Failed to generate OG image for {}: {}", username, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Lightweight HTML page with OG meta tags for social crawlers.
     */
    @GetMapping(value = "/api/v1/creators/{username}/meta", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getCreatorMeta(@PathVariable String username) {
        return creatorRepository.findByUsername(username)
                .map(creator -> {
                    BigDecimal totalTips = tipRepository.sumAmountByCreatorIdAndStatus(
                            creator.getId(), TipStatus.CONFIRMED);
                    long tipCount = tipRepository.countByCreatorIdAndStatus(
                            creator.getId(), TipStatus.CONFIRMED);

                    String displayName = escapeHtml(creator.getDisplayName());
                    String bio = creator.getBio() != null ? escapeHtml(creator.getBio()) : "Support this creator with SOL tips on SolanaTip";
                    String solAmount = totalTips != null ? totalTips.setScale(2, RoundingMode.HALF_UP).toString() : "0.00";
                    String proTag = creator.getSubscriptionPlan() == SubscriptionPlan.PRO ? " ★ Pro Creator" : "";

                    String title = displayName + " on SolanaTip" + proTag;
                    String description = bio + " | " + solAmount + " SOL received · " + tipCount + " tips";
                    String ogImageUrl = baseUrl + "/api/v1/creators/" + username + "/og-image";
                    String pageUrl = baseUrl + "/creator/" + username;

                    String html = """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8" />
                            <title>%s</title>
                            <meta name="description" content="%s" />
                            
                            <meta property="og:type" content="profile" />
                            <meta property="og:url" content="%s" />
                            <meta property="og:title" content="%s" />
                            <meta property="og:description" content="%s" />
                            <meta property="og:image" content="%s" />
                            <meta property="og:image:width" content="1200" />
                            <meta property="og:image:height" content="630" />
                            <meta property="og:site_name" content="SolanaTip" />
                            
                            <meta name="twitter:card" content="summary_large_image" />
                            <meta name="twitter:title" content="%s" />
                            <meta name="twitter:description" content="%s" />
                            <meta name="twitter:image" content="%s" />
                            
                            <meta http-equiv="refresh" content="0;url=%s" />
                        </head>
                        <body>
                            <p>Redirecting to <a href="%s">%s's profile on SolanaTip</a>...</p>
                        </body>
                        </html>
                        """.formatted(
                            title, description,
                            pageUrl, title, description, ogImageUrl,
                            title, description, ogImageUrl,
                            pageUrl, pageUrl, displayName
                    );

                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_HTML)
                            .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                            .body(html);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
