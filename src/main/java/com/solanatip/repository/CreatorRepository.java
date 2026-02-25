package com.solanatip.repository;

import com.solanatip.entity.Creator;
import com.solanatip.entity.AuthProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorRepository extends JpaRepository<Creator, UUID> {

    Optional<Creator> findByUsername(String username);

    Optional<Creator> findByWalletAddress(String walletAddress);

    Optional<Creator> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByWalletAddress(String walletAddress);

    Optional<Creator> findByAuthProviderAndOauthId(AuthProvider authProvider, String oauthId);

    Page<Creator> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            String username, String displayName, Pageable pageable);

    Optional<Creator> findByStripeCustomerId(String stripeCustomerId);
}