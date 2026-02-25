package com.solanatip.service;

import com.solanatip.entity.Creator;
import com.solanatip.entity.SubscriptionPlan;
import com.solanatip.repository.CreatorRepository;
import com.stripe.Stripe;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final CreatorRepository creatorRepository;

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.price-id-eur}")
    private String priceIdEur;

    @Value("${stripe.price-id-usd}")
    private String priceIdUsd;

    @Value("${app.base-url}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    /**
     * Create a Stripe Checkout Session for the Pro subscription.
     */
    public String createCheckoutSession(Creator creator, String currency) throws StripeException {
        String priceId = "eur".equalsIgnoreCase(currency) ? priceIdEur : priceIdUsd;

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(baseUrl + "/settings?subscription=success")
                .setCancelUrl(baseUrl + "/pricing?subscription=cancelled")
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("creator_id", creator.getId().toString())
                .putMetadata("username", creator.getUsername());

        // Reuse existing Stripe customer if we have one
        if (creator.getStripeCustomerId() != null) {
            builder.setCustomer(creator.getStripeCustomerId());
        } else if (creator.getEmail() != null) {
            // Pre-fill email — Stripe auto-creates the customer in subscription mode
            builder.setCustomerEmail(creator.getEmail());
        }

        Session session = Session.create(builder.build());
        return session.getUrl();
    }

    /**
     * Create a Stripe Customer Portal session for managing the subscription.
     */
    public String createPortalSession(Creator creator) throws StripeException {
        if (creator.getStripeCustomerId() == null) {
            throw new IllegalStateException("No Stripe customer found for this account");
        }

        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(creator.getStripeCustomerId())
                        .setReturnUrl(baseUrl + "/settings")
                        .build();

        com.stripe.model.billingportal.Session portalSession =
                com.stripe.model.billingportal.Session.create(params);

        return portalSession.getUrl();
    }

    /**
     * Process a Stripe webhook event.
     */
    @Transactional
    public void handleWebhook(String payload, String sigHeader) throws EventDataObjectDeserializationException {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            throw new RuntimeException("Invalid signature");
        }

        log.info("Stripe webhook received: {}", event.getType());

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.payment_failed" -> handlePaymentFailed(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    private void handleCheckoutCompleted(Event event) throws EventDataObjectDeserializationException {
        Session session = (Session) event.getDataObjectDeserializer()
                .deserializeUnsafe();

        String creatorId = session.getMetadata().get("creator_id");
        String customerId = session.getCustomer();
        String subscriptionId = session.getSubscription();

        if (creatorId == null) {
            log.warn("checkout.session.completed missing creator_id metadata");
            return;
        }

        creatorRepository.findById(java.util.UUID.fromString(creatorId)).ifPresent(creator -> {
            creator.setStripeCustomerId(customerId);
            creator.setStripeSubscriptionId(subscriptionId);
            creator.setSubscriptionPlan(SubscriptionPlan.PRO);
            creatorRepository.save(creator);
            log.info("Creator {} upgraded to PRO (subscription: {})", creator.getUsername(), subscriptionId);
        });
    }

    private void handleSubscriptionUpdated(Event event) throws EventDataObjectDeserializationException {
        Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                .deserializeUnsafe();

        String customerId = subscription.getCustomer();
        String status = subscription.getStatus();

        creatorRepository.findByStripeCustomerId(customerId).ifPresent(creator -> {
            if ("active".equals(status) || "trialing".equals(status)) {
                creator.setSubscriptionPlan(SubscriptionPlan.PRO);

                // Track cancellation: user cancelled but still active until period end
                if (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())
                        && subscription.getCurrentPeriodEnd() != null) {
                    creator.setSubscriptionExpiresAt(
                            LocalDateTime.ofInstant(
                                    Instant.ofEpochSecond(subscription.getCurrentPeriodEnd()),
                                    ZoneId.systemDefault()
                            )
                    );
                    log.info("Subscription cancelled for {} — active until {}",
                            creator.getUsername(), creator.getSubscriptionExpiresAt());
                } else {
                    // Active and renewing — clear any previous expiration
                    creator.setSubscriptionExpiresAt(null);
                }
            } else {
                // past_due, unpaid, canceled, incomplete_expired
                creator.setSubscriptionPlan(SubscriptionPlan.FREE);
                creator.setSubscriptionExpiresAt(null);
            }
            creatorRepository.save(creator);
            log.info("Subscription updated for {}: status={}, plan={}",
                    creator.getUsername(), status, creator.getSubscriptionPlan());
        });
    }

    private void handleSubscriptionDeleted(Event event) throws EventDataObjectDeserializationException {
        Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                .deserializeUnsafe();

        String customerId = subscription.getCustomer();

        creatorRepository.findByStripeCustomerId(customerId).ifPresent(creator -> {
            creator.setSubscriptionPlan(SubscriptionPlan.FREE);
            creator.setStripeSubscriptionId(null);
            creatorRepository.save(creator);
            log.info("Creator {} downgraded to FREE (subscription cancelled)", creator.getUsername());
        });
    }

    private void handlePaymentFailed(Event event) throws EventDataObjectDeserializationException {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .deserializeUnsafe();

        String customerId = invoice.getCustomer();

        creatorRepository.findByStripeCustomerId(customerId).ifPresent(creator -> {
            log.warn("Payment failed for creator {} (customer: {})", creator.getUsername(), customerId);
            // Don't downgrade immediately — Stripe will retry.
            // Downgrade happens on subscription.deleted after all retries fail.
        });
    }
}