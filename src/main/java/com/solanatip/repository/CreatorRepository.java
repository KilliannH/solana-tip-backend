package com.solanatip.repository;

import com.solanatip.entity.Creator;
import com.solanatip.entity.AuthProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

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

    // Pro creators first, then by createdAt desc
    @Query("SELECT c FROM Creator c ORDER BY CASE WHEN c.subscriptionPlan = 'PRO' THEN 0 ELSE 1 END, c.createdAt DESC")
    Page<Creator> findAllProFirst(Pageable pageable);

    // Pro creators first with search
    @Query("SELECT c FROM Creator c WHERE LOWER(c.username) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.displayName) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY CASE WHEN c.subscriptionPlan = 'PRO' THEN 0 ELSE 1 END, c.createdAt DESC")
    Page<Creator> searchAllProFirst(@Param("search") String search, Pageable pageable);

    @Query("SELECT c FROM Creator c WHERE c.createdAt >= :since ORDER BY c.createdAt DESC")
    List<Creator> findRecentCreators(@Param("since") LocalDateTime since, Pageable pageable);
}