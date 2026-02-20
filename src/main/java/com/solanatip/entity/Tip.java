package com.solanatip.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tips")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Tip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private Creator creator;

    @Column(nullable = false)
    private String senderWalletAddress;

    private String senderDisplayName;

    @Column(nullable = false, precision = 20, scale = 9)
    private BigDecimal amountSol;

    @Column(nullable = false, unique = true)
    private String transactionSignature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TipStatus status = TipStatus.PENDING;

    private String message;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
