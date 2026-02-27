package com.solanatip.service;

import com.solanatip.entity.Creator;
import com.solanatip.entity.RefreshToken;
import com.solanatip.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.refresh-expiration-days:30}")
    private int refreshExpirationDays;

    /**
     * Create a new refresh token for a creator.
     */
    @Transactional
    public String createRefreshToken(Creator creator) {
        String tokenValue = generateTokenValue();

        RefreshToken token = RefreshToken.builder()
                .token(tokenValue)
                .creator(creator)
                .expiresAt(LocalDateTime.now().plusDays(refreshExpirationDays))
                .revoked(false)
                .build();

        refreshTokenRepository.save(token);
        return tokenValue;
    }

    /**
     * Rotate: validate old token, revoke it, issue new one.
     * Returns the creator associated with the token.
     */
    @Transactional
    public RotateResult rotateRefreshToken(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (!token.isValid()) {
            // Possible token reuse attack — revoke all tokens for this creator
            if (token.isRevoked()) {
                log.warn("Refresh token reuse detected for creator: {}. Revoking all tokens.", token.getCreator().getUsername());
                refreshTokenRepository.revokeAllByCreatorId(token.getCreator().getId());
            }
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        // Revoke old token
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        // Issue new token
        Creator creator = token.getCreator();
        String newTokenValue = createRefreshToken(creator);

        return new RotateResult(creator, newTokenValue);
    }

    /**
     * Revoke all refresh tokens for a creator (logout everywhere).
     */
    @Transactional
    public void revokeAllTokens(Creator creator) {
        refreshTokenRepository.revokeAllByCreatorId(creator.getId());
        log.info("Revoked all refresh tokens for: {}", creator.getUsername());
    }

    /**
     * Clean up expired/revoked tokens daily.
     */
    @Scheduled(cron = "0 0 3 * * ?") // 3 AM every day
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredAndRevoked(LocalDateTime.now());
        log.info("Cleaned up expired refresh tokens");
    }

    private String generateTokenValue() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record RotateResult(Creator creator, String refreshToken) {}
}
