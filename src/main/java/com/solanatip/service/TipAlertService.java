package com.solanatip.service;

import com.solanatip.entity.Tip;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class TipAlertService {

    // Map of creator username -> list of active SSE connections
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Register a new SSE connection for a creator's alert overlay.
     */
    public SseEmitter subscribe(String username) {
        // 30 min timeout (OBS keeps connection open)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitters.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(username, emitter));
        emitter.onTimeout(() -> removeEmitter(username, emitter));
        emitter.onError(e -> removeEmitter(username, emitter));

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\"}"));
        } catch (IOException e) {
            removeEmitter(username, emitter);
        }

        log.info("Alert overlay connected for creator: {} (total: {})",
                username, emitters.getOrDefault(username, new CopyOnWriteArrayList<>()).size());

        return emitter;
    }

    /**
     * Broadcast a tip event to all connected overlays for a creator.
     */
    public void broadcastTip(String creatorUsername, Tip tip) {
        CopyOnWriteArrayList<SseEmitter> creatorEmitters = emitters.get(creatorUsername);
        if (creatorEmitters == null || creatorEmitters.isEmpty()) {
            return;
        }

        String senderName = tip.getSenderDisplayName() != null && !tip.getSenderDisplayName().isBlank()
                ? tip.getSenderDisplayName()
                : shortenWallet(tip.getSenderWalletAddress());

        String data = String.format(
                "{\"id\":\"%s\",\"amount\":\"%s\",\"sender\":\"%s\",\"message\":\"%s\"}",
                tip.getId(),
                tip.getAmountSol().toPlainString(),
                escapeJson(senderName),
                tip.getMessage() != null ? escapeJson(tip.getMessage()) : ""
        );

        for (SseEmitter emitter : creatorEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("tip")
                        .data(data));
            } catch (IOException e) {
                removeEmitter(creatorUsername, emitter);
            }
        }

        log.info("Tip alert broadcast to {} overlay(s) for creator: {}",
                creatorEmitters.size(), creatorUsername);
    }

    /**
     * Send a test alert for preview purposes.
     */
    public void broadcastTest(String creatorUsername) {
        CopyOnWriteArrayList<SseEmitter> creatorEmitters = emitters.get(creatorUsername);
        if (creatorEmitters == null || creatorEmitters.isEmpty()) {
            return;
        }

        String data = "{\"id\":\"test\",\"amount\":\"1.337\",\"sender\":\"TestUser\",\"message\":\"This is a test alert! Your OBS overlay is working.\"}";

        for (SseEmitter emitter : creatorEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("tip")
                        .data(data));
            } catch (IOException e) {
                removeEmitter(creatorUsername, emitter);
            }
        }

        log.info("Test alert sent to {} overlay(s) for creator: {}",
                creatorEmitters.size(), creatorUsername);
    }

    private void removeEmitter(String username, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(username);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(username);
            }
        }
    }

    private String shortenWallet(String wallet) {
        if (wallet == null || wallet.length() < 10) return wallet != null ? wallet : "Anonymous";
        return wallet.substring(0, 6) + "..." + wallet.substring(wallet.length() - 4);
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}