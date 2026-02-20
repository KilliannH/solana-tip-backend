package com.solanatip.service;

import com.solanatip.dto.CreatorDto;
import com.solanatip.entity.Creator;
import com.solanatip.entity.TipStatus;
import com.solanatip.exception.ApiExceptions.DuplicateResourceException;
import com.solanatip.exception.ApiExceptions.ResourceNotFoundException;
import com.solanatip.repository.CreatorRepository;
import com.solanatip.repository.TipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorService {

    private final CreatorRepository creatorRepository;
    private final TipRepository tipRepository;

    @Transactional
    public CreatorDto.Response createCreator(CreatorDto.CreateRequest request) {
        if (creatorRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username '" + request.getUsername() + "' is already taken");
        }
        if (creatorRepository.existsByWalletAddress(request.getWalletAddress())) {
            throw new DuplicateResourceException("Wallet address is already registered");
        }

        Creator creator = Creator.builder()
                .username(request.getUsername())
                .displayName(request.getDisplayName())
                .bio(request.getBio())
                .walletAddress(request.getWalletAddress())
                .avatarUrl(request.getAvatarUrl())
                .bannerUrl(request.getBannerUrl())
                .themeColor(request.getThemeColor() != null ? request.getThemeColor() : "cyan")
                .build();

        return toResponse(creatorRepository.save(creator));
    }

    public CreatorDto.Response getCreatorByUsername(String username) {
        Creator creator = creatorRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found: " + username));
        return toResponse(creator);
    }

    public CreatorDto.Response getCreatorById(UUID id) {
        Creator creator = creatorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));
        return toResponse(creator);
    }

    public List<CreatorDto.Response> getAllCreators() {
        return creatorRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CreatorDto.Response updateCreator(String username, CreatorDto.UpdateRequest request) {
        Creator creator = creatorRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found: " + username));

        if (request.getDisplayName() != null) creator.setDisplayName(request.getDisplayName());
        if (request.getBio() != null) creator.setBio(request.getBio());
        if (request.getAvatarUrl() != null) creator.setAvatarUrl(request.getAvatarUrl());
        if (request.getBannerUrl() != null) creator.setBannerUrl(request.getBannerUrl());
        if (request.getThemeColor() != null) creator.setThemeColor(request.getThemeColor());
        if (request.getYoutubeUrl() != null) creator.setYoutubeUrl(request.getYoutubeUrl());
        if (request.getTwitchUrl() != null) creator.setTwitchUrl(request.getTwitchUrl());
        if (request.getTiktokUrl() != null) creator.setTiktokUrl(request.getTiktokUrl());
        if (request.getTwitterUrl() != null) creator.setTwitterUrl(request.getTwitterUrl());

        return toResponse(creatorRepository.save(creator));
    }

    @Transactional
    public void deleteCreator(String username) {
        Creator creator = creatorRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found: " + username));
        creatorRepository.delete(creator);
    }

    Creator getCreatorEntityByUsername(String username) {
        return creatorRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found: " + username));
    }

    private CreatorDto.Response toResponse(Creator creator) {
        return CreatorDto.Response.builder()
                .id(creator.getId())
                .username(creator.getUsername())
                .displayName(creator.getDisplayName())
                .bio(creator.getBio())
                .walletAddress(creator.getWalletAddress())
                .avatarUrl(creator.getAvatarUrl())
                .bannerUrl(creator.getBannerUrl())
                .themeColor(creator.getThemeColor())
                .youtubeUrl(creator.getYoutubeUrl())
                .twitchUrl(creator.getTwitchUrl())
                .tiktokUrl(creator.getTiktokUrl())
                .twitterUrl(creator.getTwitterUrl())
                .totalTipsReceived(tipRepository.sumAmountByCreatorIdAndStatus(creator.getId(), TipStatus.CONFIRMED))
                .tipCount(tipRepository.countByCreatorIdAndStatus(creator.getId(), TipStatus.CONFIRMED))
                .createdAt(creator.getCreatedAt())
                .build();
    }
}