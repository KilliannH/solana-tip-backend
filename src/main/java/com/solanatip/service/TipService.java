package com.solanatip.service;

import com.solanatip.dto.TipDto;
import com.solanatip.entity.Creator;
import com.solanatip.entity.Tip;
import com.solanatip.entity.TipStatus;
import com.solanatip.exception.ApiExceptions.DuplicateResourceException;
import com.solanatip.exception.ApiExceptions.TransactionVerificationException;
import com.solanatip.repository.TipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TipService {

    private final TipRepository tipRepository;
    private final CreatorService creatorService;
    private final SolanaRpcService solanaRpcService;

    /**
     * Record and verify a new tip.
     * Flow:
     * 1. Frontend sends SOL via Phantom Wallet
     * 2. Frontend sends tx signature to this endpoint
     * 3. Backend verifies on-chain that the tx is valid
     * 4. Tip is stored with CONFIRMED status
     */
    @Transactional
    public TipDto.Response createTip(TipDto.CreateRequest request) {
        // Check for duplicate transaction
        if (tipRepository.existsByTransactionSignature(request.getTransactionSignature())) {
            throw new DuplicateResourceException("Transaction already recorded: " + request.getTransactionSignature());
        }

        // Find the creator
        Creator creator = creatorService.getCreatorEntityByUsername(request.getCreatorUsername());

        // Verify the transaction on-chain
        SolanaRpcService.TransactionVerification verification;
        try {
            verification = solanaRpcService.verifyTransaction(
                    request.getTransactionSignature(),
                    creator.getWalletAddress(),
                    request.getAmountSol()
            );
        } catch (TransactionVerificationException e) {
            // Save as FAILED for audit trail
            Tip failedTip = buildTip(request, creator, TipStatus.FAILED);
            tipRepository.save(failedTip);
            throw e;
        }

        // Verify sender matches
        if (!verification.sender().equals(request.getSenderWalletAddress())) {
            throw new TransactionVerificationException("Sender wallet address does not match transaction");
        }

        // Save confirmed tip
        Tip tip = buildTip(request, creator, TipStatus.CONFIRMED);
        Tip savedTip = tipRepository.save(tip);

        log.info("Tip confirmed: {} SOL from {} to {} (tx: {})",
                request.getAmountSol(), request.getSenderWalletAddress(),
                creator.getUsername(), request.getTransactionSignature());

        return toResponse(savedTip);
    }

    public Page<TipDto.Response> getTipsByCreator(String username, int page, int size) {
        Creator creator = creatorService.getCreatorEntityByUsername(username);
        return tipRepository.findByCreatorIdOrderByCreatedAtDesc(creator.getId(), PageRequest.of(page, size))
                .map(this::toResponse);
    }

    public Page<TipDto.Response> getTipsBySender(String walletAddress, int page, int size) {
        return tipRepository.findBySenderWalletAddressOrderByCreatedAtDesc(walletAddress, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    private Tip buildTip(TipDto.CreateRequest request, Creator creator, TipStatus status) {
        return Tip.builder()
                .creator(creator)
                .senderWalletAddress(request.getSenderWalletAddress())
                .senderDisplayName(request.getSenderDisplayName())
                .amountSol(request.getAmountSol())
                .transactionSignature(request.getTransactionSignature())
                .status(status)
                .message(request.getMessage())
                .build();
    }

    private TipDto.Response toResponse(Tip tip) {
        return TipDto.Response.builder()
                .id(tip.getId())
                .creatorUsername(tip.getCreator().getUsername())
                .creatorDisplayName(tip.getCreator().getDisplayName())
                .senderWalletAddress(tip.getSenderWalletAddress())
                .senderDisplayName(tip.getSenderDisplayName())
                .amountSol(tip.getAmountSol())
                .transactionSignature(tip.getTransactionSignature())
                .status(tip.getStatus().name())
                .message(tip.getMessage())
                .createdAt(tip.getCreatedAt())
                .build();
    }
}
