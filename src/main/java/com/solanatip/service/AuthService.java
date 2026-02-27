package com.solanatip.service;

import com.solanatip.dto.AuthDto;
import com.solanatip.entity.AuthProvider;
import com.solanatip.entity.Creator;
import com.solanatip.exception.ApiExceptions.DuplicateResourceException;
import com.solanatip.exception.ApiExceptions.ResourceNotFoundException;
import com.solanatip.repository.CreatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String NONCE_MESSAGE_TEMPLATE =
            "Sign this message to authenticate with SolanaTip.\n\nNonce: %s";

    private final CreatorRepository creatorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final WalletSignatureService walletSignatureService;
    private final EmailVerificationService emailVerificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    // ========== Email/Password Flow ==========

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        validateUniqueness(request.getUsername(), request.getEmail(), request.getWalletAddress());

        Creator creator = Creator.builder()
                .username(request.getUsername())
                .displayName(request.getDisplayName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .walletAddress(request.getWalletAddress())
                .bio(request.getBio())
                .avatarUrl(request.getAvatarUrl())
                .authProvider(AuthProvider.EMAIL)
                .build();

        creatorRepository.save(creator);
        log.info("Creator registered via email: {}", creator.getUsername());

        // Send verification email (non-blocking)
        emailVerificationService.sendVerificationEmail(creator);

        return buildAuthResponse(creator);
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        Creator creator = creatorRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), creator.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        log.info("Creator logged in via email: {}", creator.getUsername());
        return buildAuthResponse(creator);
    }

    // ========== Wallet Signature Flow ==========

    /**
     * Step 1: Generate a nonce for the wallet to sign.
     * If the wallet is already registered, returns registered=true.
     */
    @Transactional
    public AuthDto.WalletNonceResponse generateNonce(AuthDto.WalletNonceRequest request) {
        String nonce = generateSecureNonce();
        String message = String.format(NONCE_MESSAGE_TEMPLATE, nonce);
        boolean registered = false;

        var optionalCreator = creatorRepository.findByWalletAddress(request.getWalletAddress());
        if (optionalCreator.isPresent()) {
            // Existing creator: store nonce for verification
            Creator creator = optionalCreator.get();
            creator.setWalletNonce(nonce);
            creatorRepository.save(creator);
            registered = true;
        } else {
            // New wallet: store nonce temporarily
            // For new wallets, we'll verify the nonce on registration
            // Store in a lightweight way — we use walletNonce field after creation
        }

        return AuthDto.WalletNonceResponse.builder()
                .nonce(nonce)
                .message(message)
                .registered(registered)
                .build();
    }

    /**
     * Step 2a: Login with wallet signature (existing creator).
     */
    @Transactional
    public AuthDto.AuthResponse walletLogin(AuthDto.WalletLoginRequest request) {
        Creator creator = creatorRepository.findByWalletAddress(request.getWalletAddress())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not registered. Please sign up first."));

        if (creator.getWalletNonce() == null) {
            throw new BadCredentialsException("No pending nonce. Request a nonce first.");
        }

        String expectedMessage = String.format(NONCE_MESSAGE_TEMPLATE, creator.getWalletNonce());
        boolean valid = walletSignatureService.verifySignature(
                request.getWalletAddress(), expectedMessage, request.getSignature());

        if (!valid) {
            throw new BadCredentialsException("Invalid wallet signature");
        }

        // Invalidate nonce after use (one-time use)
        creator.setWalletNonce(null);
        creatorRepository.save(creator);

        log.info("Creator logged in via wallet: {}", creator.getUsername());
        return buildAuthResponse(creator);
    }

    /**
     * Step 2b: Register + login with wallet signature (new creator).
     */
    @Transactional
    public AuthDto.AuthResponse walletRegister(AuthDto.WalletRegisterRequest request, String nonce) {
        validateUniqueness(request.getUsername(), null, request.getWalletAddress());

        String expectedMessage = String.format(NONCE_MESSAGE_TEMPLATE, nonce);
        boolean valid = walletSignatureService.verifySignature(
                request.getWalletAddress(), expectedMessage, request.getSignature());

        if (!valid) {
            throw new BadCredentialsException("Invalid wallet signature");
        }

        Creator creator = Creator.builder()
                .username(request.getUsername())
                .displayName(request.getDisplayName())
                .walletAddress(request.getWalletAddress())
                .bio(request.getBio())
                .avatarUrl(request.getAvatarUrl())
                .authProvider(AuthProvider.WALLET)
                .emailVerified(true)
                .build();

        creatorRepository.save(creator);
        log.info("Creator registered via wallet: {}", creator.getUsername());

        return buildAuthResponse(creator);
    }

    // ========== Helpers ==========

    private void validateUniqueness(String username, String email, String walletAddress) {
        if (creatorRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken");
        }
        if (email != null && creatorRepository.findByEmail(email).isPresent()) {
            throw new DuplicateResourceException("Email is already registered");
        }
        if (walletAddress != null && creatorRepository.existsByWalletAddress(walletAddress)) {
            throw new DuplicateResourceException("Wallet address is already registered");
        }
    }

    private AuthDto.AuthResponse buildAuthResponse(Creator creator) {
        String accessToken = jwtService.generateToken(creator.getUsername(), creator.getWalletAddress());
        String refreshToken = refreshTokenService.createRefreshToken(creator);
        return AuthDto.AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .type("Bearer")
                .username(creator.getUsername())
                .walletAddress(creator.getWalletAddress())
                .build();
    }

    @Transactional
    public AuthDto.AuthResponse refreshToken(String refreshTokenValue) {
        RefreshTokenService.RotateResult result = refreshTokenService.rotateRefreshToken(refreshTokenValue);
        Creator creator = result.creator();
        String accessToken = jwtService.generateToken(creator.getUsername(), creator.getWalletAddress());
        return AuthDto.AuthResponse.builder()
                .token(accessToken)
                .refreshToken(result.refreshToken())
                .type("Bearer")
                .username(creator.getUsername())
                .walletAddress(creator.getWalletAddress())
                .build();
    }

    @Transactional
    public void logout(String username) {
        Creator creator = creatorRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));
        refreshTokenService.revokeAllTokens(creator);
    }

    private String generateSecureNonce() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}