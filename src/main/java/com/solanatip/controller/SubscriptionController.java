package com.solanatip.controller;

import com.solanatip.entity.Creator;
import com.solanatip.repository.CreatorRepository;
import com.solanatip.service.StripeService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

    private final StripeService stripeService;
    private final CreatorRepository creatorRepository;

    /**
     * Create a Stripe Checkout Session for the Pro subscription.
     * Returns the Stripe Checkout URL for frontend redirect.
     */
    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckoutSession(
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        String username = authentication.getName();
        Creator creator = creatorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        // Already pro?
        if (creator.getSubscriptionPlan() == com.solanatip.entity.SubscriptionPlan.PRO) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "You are already subscribed to Pro"));
        }

        String currency = body.getOrDefault("currency", "eur");

        try {
            String checkoutUrl = stripeService.createCheckoutSession(creator, currency);
            return ResponseEntity.ok(Map.of("url", checkoutUrl));
        } catch (StripeException e) {
            log.error("Failed to create checkout session for {}: {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create checkout session"));
        }
    }

    /**
     * Create a Stripe Customer Portal session for managing the subscription.
     * Returns the portal URL for frontend redirect.
     */
    @PostMapping("/portal")
    public ResponseEntity<?> createPortalSession(Authentication authentication) {

        String username = authentication.getName();
        Creator creator = creatorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        try {
            String portalUrl = stripeService.createPortalSession(creator);
            return ResponseEntity.ok(Map.of("url", portalUrl));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (StripeException e) {
            log.error("Failed to create portal session for {}: {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create portal session"));
        }
    }

    /**
     * Stripe Webhook endpoint.
     * Must receive raw body for signature verification.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            stripeService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok("ok");
        } catch (RuntimeException e) {
            log.error("Webhook processing failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Webhook error: " + e.getMessage());
        }
    }
}
