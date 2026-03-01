package com.solanatip.repository;

import com.solanatip.entity.Tip;
import com.solanatip.entity.TipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TipRepository extends JpaRepository<Tip, UUID> {

    Page<Tip> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId, Pageable pageable);

    Page<Tip> findBySenderWalletAddressOrderByCreatedAtDesc(String walletAddress, Pageable pageable);

    Optional<Tip> findByTransactionSignature(String transactionSignature);

    boolean existsByTransactionSignature(String transactionSignature);

    @Query("SELECT COALESCE(SUM(t.amountSol), 0) FROM Tip t WHERE t.creator.id = :creatorId AND t.status = :status")
    BigDecimal sumAmountByCreatorIdAndStatus(@Param("creatorId") UUID creatorId, @Param("status") TipStatus status);

    @Query("SELECT COUNT(t) FROM Tip t WHERE t.creator.id = :creatorId AND t.status = :status")
    long countByCreatorIdAndStatus(@Param("creatorId") UUID creatorId, @Param("status") TipStatus status);

    // ========== Analytics queries ==========

    @Query("SELECT COALESCE(SUM(t.amountSol), 0) FROM Tip t WHERE t.creator.id = :creatorId AND t.status = 'CONFIRMED' AND t.createdAt >= :since")
    BigDecimal sumConfirmedSince(@Param("creatorId") UUID creatorId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(t) FROM Tip t WHERE t.creator.id = :creatorId AND t.status = 'CONFIRMED' AND t.createdAt >= :since")
    long countConfirmedSince(@Param("creatorId") UUID creatorId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT t.senderWalletAddress) FROM Tip t WHERE t.creator.id = :creatorId AND t.status = 'CONFIRMED'")
    long countUniqueSupporters(@Param("creatorId") UUID creatorId);

    @Query(value = "SELECT DATE(t.created_at) as tip_date, COALESCE(SUM(t.amount_sol), 0) as total_amount, COUNT(*) as tip_count " +
            "FROM tips t WHERE t.creator_id = :creatorId AND t.status = 'CONFIRMED' AND t.created_at >= :since " +
            "GROUP BY DATE(t.created_at) ORDER BY tip_date ASC", nativeQuery = true)
    List<Object[]> getDailyRevenue(@Param("creatorId") UUID creatorId, @Param("since") LocalDateTime since);

    @Query(value = "SELECT t.sender_wallet_address, " +
            "COALESCE(MAX(t.sender_display_name), SUBSTRING(t.sender_wallet_address, 1, 4) || '...' || SUBSTRING(t.sender_wallet_address, LENGTH(t.sender_wallet_address) - 3, 4)) as display_name, " +
            "SUM(t.amount_sol) as total_amount, COUNT(*) as tip_count " +
            "FROM tips t WHERE t.creator_id = :creatorId AND t.status = 'CONFIRMED' " +
            "GROUP BY t.sender_wallet_address ORDER BY total_amount DESC LIMIT :lim", nativeQuery = true)
    List<Object[]> getTopSupporters(@Param("creatorId") UUID creatorId, @Param("lim") int lim);

    @Query("SELECT t FROM Tip t WHERE t.creator.id = :creatorId AND t.status = 'CONFIRMED' ORDER BY t.createdAt DESC")
    List<Tip> getRecentConfirmedTips(@Param("creatorId") UUID creatorId, Pageable pageable);

    @Query("SELECT t FROM Tip t JOIN FETCH t.creator WHERE t.status = 'CONFIRMED' ORDER BY t.createdAt DESC")
    List<Tip> getRecentConfirmedTipsGlobal(Pageable pageable);

    @Query("SELECT t.creator, SUM(t.amountSol) FROM Tip t WHERE t.status = 'CONFIRMED' GROUP BY t.creator HAVING SUM(t.amountSol) >= 1 ORDER BY SUM(t.amountSol) DESC")
    List<Object[]> getCreatorTotals();
}
