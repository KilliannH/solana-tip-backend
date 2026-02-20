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
}
