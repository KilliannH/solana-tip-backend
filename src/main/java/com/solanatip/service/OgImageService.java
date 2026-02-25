package com.solanatip.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class OgImageService {

    @Value("${app.base-url:https://solana-tip.com}")
    private String baseUrl;

    @Value("${screenshot.service.url:http://screenshot:3333}")
    private String screenshotServiceUrl;

    private final WebClient webClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024)) // 5MB
            .build();

    // Simple in-memory cache: username -> {bytes, timestamp}
    private final Map<String, CachedImage> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

    public byte[] generateOgImage(String username) {
        // Check cache
        CachedImage cached = cache.get(username);
        if (cached != null && !cached.isExpired()) {
            log.debug("OG image cache hit for {}", username);
            return cached.data;
        }

        // Call screenshot service
        String targetUrl = baseUrl + "/og-preview/" + username;
        String screenshotUrl = screenshotServiceUrl + "/screenshot?url=" + targetUrl;

        log.info("Generating OG screenshot for {} via {}", username, targetUrl);

        try {
            byte[] imageBytes = webClient.get()
                    .uri(screenshotUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            if (imageBytes != null && imageBytes.length > 0) {
                cache.put(username, new CachedImage(imageBytes));
                log.info("OG image generated for {} ({} bytes)", username, imageBytes.length);
                return imageBytes;
            }

            throw new RuntimeException("Empty response from screenshot service");
        } catch (Exception e) {
            log.error("Screenshot failed for {}: {}", username, e.getMessage());

            // Return cached version if available (even if expired)
            if (cached != null) {
                log.warn("Returning stale cached OG image for {}", username);
                return cached.data;
            }

            throw new RuntimeException("Failed to generate OG image", e);
        }
    }

    /**
     * Invalidate cached OG image (call when profile is updated).
     */
    public void invalidateCache(String username) {
        cache.remove(username);
    }

    private static class CachedImage {
        final byte[] data;
        final long createdAt;

        CachedImage(byte[] data) {
            this.data = data;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }
}
