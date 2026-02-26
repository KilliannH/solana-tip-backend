package com.solanatip.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "creators")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Creator {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String displayName;

    @Column(length = 500)
    private String bio;

    @Column(unique = true)
    private String walletAddress;

    private String avatarUrl;

    private String bannerUrl;

    /** Neon theme color key: cyan, purple, pink, green, blue */
    @Column(length = 20)
    @Builder.Default
    private String themeColor = "cyan";

    // --- Social Links ---

    private String youtubeUrl;
    private String twitchUrl;
    private String tiktokUrl;
    private String twitterUrl;

    // --- Auth fields ---

    @Column(unique = true)
    private String email;

    private String password;

    private String walletNonce;

    /** OAuth provider-specific user ID */
    private String oauthId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.EMAIL;

    @Builder.Default
    private boolean emailVerified = false;

    @Builder.Default
    private boolean admin = false;

    // --- Notification Preferences ---

    @Builder.Default
    private boolean notifyTipReceived = true;

    @Builder.Default
    private boolean notifyMarketing = true;

    // --- Stripe Subscription ---

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.FREE;

    @Column(unique = true)
    private String stripeCustomerId;

    private String stripeSubscriptionId;

    private LocalDateTime subscriptionExpiresAt;

    // --- OBS Alert Customization (Pro) ---

    @Column(length = 20)
    @Builder.Default
    private String alertColor = "cyan";

    @Column(length = 20)
    @Builder.Default
    private String alertAnimation = "slide";

    @Column(length = 20)
    @Builder.Default
    private String alertSound = "chime";

    @Builder.Default
    private int alertDuration = 8;

    @Column(length = 500)
    private String alertImageUrl;

    @Builder.Default
    private boolean showQrCode = false;

    // --- Relations ---

    @OneToMany(mappedBy = "creator", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Tip> tipsReceived = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}