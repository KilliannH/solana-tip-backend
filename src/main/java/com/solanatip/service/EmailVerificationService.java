package com.solanatip.service;

import com.solanatip.entity.Creator;
import com.solanatip.entity.EmailVerificationToken;
import com.solanatip.exception.ApiExceptions;
import com.solanatip.repository.CreatorRepository;
import com.solanatip.repository.EmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final CreatorRepository creatorRepository;
    private final JavaMailSender mailSender;

    @Value("${app.base-url:https://solana-tip.com}")
    private String baseUrl;

    @Value("${app.mail.from:noreply@solana-tip.com}")
    private String fromEmail;

    /**
     * Send a verification email to the creator.
     */
    @Transactional
    public void sendVerificationEmail(Creator creator) {
        if (creator.getEmail() == null || creator.getEmail().isBlank()) {
            log.warn("Cannot send verification email: no email for creator {}", creator.getUsername());
            return;
        }

        if (creator.isEmailVerified()) {
            return;
        }

        // Delete old tokens
        tokenRepository.deleteByCreatorId(creator.getId());

        // Create new token (valid 24h)
        String token = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .token(token)
                .creator(creator)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        tokenRepository.save(verificationToken);

        // Send email
        String verifyUrl = baseUrl + "/verify-email?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(creator.getEmail());
        message.setSubject("SolanaTip — Verify your email");
        message.setText(
                "Hey " + creator.getDisplayName() + "!\n\n" +
                        "Welcome to SolanaTip. Please verify your email by clicking this link:\n\n" +
                        verifyUrl + "\n\n" +
                        "This link expires in 24 hours.\n\n" +
                        "If you didn't create this account, you can ignore this email.\n\n" +
                        "— SolanaTip"
        );

        try {
            mailSender.send(message);
            log.info("Verification email sent to {}", creator.getEmail());
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", creator.getEmail(), e.getMessage());
        }
    }

    /**
     * Verify an email token.
     */
    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Invalid verification token"));

        if (verificationToken.isExpired()) {
            tokenRepository.delete(verificationToken);
            throw new ApiExceptions.BadRequestException("Verification link has expired. Please request a new one.");
        }

        Creator creator = verificationToken.getCreator();
        creator.setEmailVerified(true);
        creatorRepository.save(creator);

        tokenRepository.delete(verificationToken);
        log.info("Email verified for creator {}", creator.getUsername());
    }

    /**
     * Resend verification email.
     */
    @Transactional
    public void resendVerification(String username) {
        Creator creator = creatorRepository.findByUsername(username)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Creator not found"));

        if (creator.isEmailVerified()) {
            throw new ApiExceptions.BadRequestException("Email is already verified");
        }

        sendVerificationEmail(creator);
    }
}