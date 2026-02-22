package com.solanatip.config;

import com.solanatip.entity.AuthProvider;
import com.solanatip.entity.Creator;
import com.solanatip.repository.CreatorRepository;
import com.solanatip.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final CreatorRepository creatorRepository;
    private final JwtService jwtService;

    @Value("${app.base-url:https://solana-tip.com}")
    private String baseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId();
        OAuth2User oAuth2User = token.getPrincipal();

        // Extract info based on provider
        String oauthId = null, email = null, name = null, avatarUrl = null;

        switch (provider) {
            case "google" -> {
                oauthId = oAuth2User.getAttribute("sub");
                email = oAuth2User.getAttribute("email");
                name = oAuth2User.getAttribute("name");
                avatarUrl = oAuth2User.getAttribute("picture");
            }
            case "discord" -> {
                oauthId = oAuth2User.getAttribute("id");
                email = oAuth2User.getAttribute("email");
                name = oAuth2User.getAttribute("global_name");
                if (name == null) name = oAuth2User.getAttribute("username");
                String discordAvatar = oAuth2User.getAttribute("avatar");
                if (discordAvatar != null && oauthId != null) {
                    avatarUrl = "https://cdn.discordapp.com/avatars/" + oauthId + "/" + discordAvatar + ".png?size=256";
                }
            }
            case "twitter" -> {
                // Twitter v2 returns nested { data: { id, name, username, profile_image_url } }
                Object dataObj = oAuth2User.getAttribute("data");
                if (dataObj instanceof Map<?, ?> data) {
                    oauthId = String.valueOf(data.get("id"));
                    name = String.valueOf(data.get("name"));
                    Object pic = data.get("profile_image_url");
                    if (pic != null) avatarUrl = pic.toString().replace("_normal", "_400x400");
                } else {
                    // Fallback: flat structure
                    oauthId = oAuth2User.getAttribute("id");
                    name = oAuth2User.getAttribute("name");
                }
            }
        }

        if (oauthId == null) {
            log.error("Could not extract OAuth ID from {} response", provider);
            response.sendRedirect(baseUrl + "/auth?error=oauth_failed");
            return;
        }

        AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase());

        // 1. Find by OAuth ID
        Optional<Creator> existing = creatorRepository.findByAuthProviderAndOauthId(authProvider, oauthId);

        Creator creator;
        if (existing.isPresent()) {
            creator = existing.get();
            // Update avatar if changed
            if (avatarUrl != null && !avatarUrl.equals(creator.getAvatarUrl())) {
                creator.setAvatarUrl(avatarUrl);
                creator = creatorRepository.save(creator);
            }
        } else {
            // 2. Check if email already exists (link accounts)
            if (email != null) {
                Optional<Creator> byEmail = creatorRepository.findByEmail(email);
                if (byEmail.isPresent()) {
                    creator = byEmail.get();
                    creator.setOauthId(oauthId);
                    // Don't change authProvider — keep original
                    creator = creatorRepository.save(creator);
                    log.info("Linked {} OAuth to existing account: {}", provider, creator.getUsername());
                } else {
                    creator = createNewCreator(authProvider, oauthId, email, name, avatarUrl);
                }
            } else {
                creator = createNewCreator(authProvider, oauthId, email, name, avatarUrl);
            }
        }

        // Generate JWT
        String jwt = jwtService.generateToken(creator.getUsername(), creator.getWalletAddress());

        String redirectUrl = baseUrl + "/auth/callback?token=" + URLEncoder.encode(jwt, StandardCharsets.UTF_8)
                + "&username=" + URLEncoder.encode(creator.getUsername(), StandardCharsets.UTF_8);

        log.info("OAuth {} login successful for user: {}", provider, creator.getUsername());
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private Creator createNewCreator(AuthProvider authProvider, String oauthId, String email,
                                     String name, String avatarUrl) {
        String username = generateUsername(name);
        String displayName = name != null ? name : username;

        Creator creator = Creator.builder()
                .username(username)
                .displayName(displayName)
                .email(email)
                .emailVerified(true)
                .authProvider(authProvider)
                .oauthId(oauthId)
                .avatarUrl(avatarUrl)
                .build();

        creator = creatorRepository.save(creator);
        log.info("Created new {} user: {} ({})", authProvider, username, email);
        return creator;
    }

    private String generateUsername(String name) {
        if (name == null || name.isBlank()) name = "user";

        // Clean: lowercase, remove special chars, replace spaces with underscores
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (base.isEmpty()) base = "user";
        if (base.length() > 15) base = base.substring(0, 15);

        // Check uniqueness, add suffix if needed
        String candidate = base;
        int attempts = 0;
        while (creatorRepository.existsByUsername(candidate)) {
            int suffix = ThreadLocalRandom.current().nextInt(100, 9999);
            candidate = base + suffix;
            if (++attempts > 20) {
                candidate = base + System.currentTimeMillis() % 100000;
                break;
            }
        }
        return candidate;
    }
}