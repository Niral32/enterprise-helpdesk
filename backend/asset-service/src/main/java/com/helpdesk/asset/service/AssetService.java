package com.helpdesk.asset.service;

import com.helpdesk.asset.dto.AssetDTO;
import com.helpdesk.asset.entity.Asset;
import com.helpdesk.asset.exception.AssetNotFoundException;
import com.helpdesk.asset.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Asset Service - Business logic for asset management
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AssetService {

    private final AssetRepository assetRepository;

    /**
     * Create a new asset
     */
    public AssetDTO createAsset(AssetDTO assetDTO) {
        log.info("Creating new asset with serial number: {}", assetDTO.getSerialNumber());

        // Check if serial number already exists
        if (assetRepository.findBySerialNumber(assetDTO.getSerialNumber()).isPresent()) {
            throw new IllegalArgumentException("Asset with serial number already exists");
        }

        Asset asset = Asset.builder()
            .name(assetDTO.getName())
            .assetType(assetDTO.getAssetType())
            .serialNumber(assetDTO.getSerialNumber())
            .description(assetDTO.getDescription())
            .status(Asset.AssetStatus.AVAILABLE)
            .assignedTo(assetDTO.getAssignedTo())
            .location(assetDTO.getLocation())
            .purchaseDate(assetDTO.getPurchaseDate())
            .vendor(assetDTO.getVendor())
            .cost(assetDTO.getCost())
            .build();

        Asset savedAsset = assetRepository.save(asset);
        log.info("Asset created successfully with ID: {}", savedAsset.getId());

        return AssetDTO.fromEntity(savedAsset);
    }

    /**
     * Get all assets
     */
    @Transactional(readOnly = true)
    public List<AssetDTO> getAllAssets() {
        log.info("Fetching all assets");
        return assetRepository.findAll().stream()
            .map(AssetDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Get asset by ID
     */
    @Transactional(readOnly = true)
    public AssetDTO getAssetById(Long id) {
        log.info("Fetching asset with ID: {}", id);
        Asset asset = assetRepository.findById(id)
            .orElseThrow(() -> {
                log.error("Asset not found with ID: {}", id);
                return new AssetNotFoundException("Asset not found with ID: " + id);
            });
        return AssetDTO.fromEntity(asset);
    }

    /**
     * Update asset
     */
    public AssetDTO updateAsset(Long id, AssetDTO assetDTO) {
        log.info("Updating asset with ID: {}", id);

        Asset asset = assetRepository.findById(id)
            .orElseThrow(() -> new AssetNotFoundException("Asset not found with ID: " + id));

        if (assetDTO.getName() != null) {
            asset.setName(assetDTO.getName());
        }
        if (assetDTO.getAssetType() != null) {
            asset.setAssetType(assetDTO.getAssetType());
        }
        if (assetDTO.getDescription() != null) {
            asset.setDescription(assetDTO.getDescription());
        }
        if (assetDTO.getStatus() != null) {
            asset.setStatus(Asset.AssetStatus.valueOf(assetDTO.getStatus()));
        }
        if (assetDTO.getAssignedTo() != null) {
            asset.setAssignedTo(assetDTO.getAssignedTo());
        }
        if (assetDTO.getLocation() != null) {
            asset.setLocation(assetDTO.getLocation());
        }
        if (assetDTO.getPurchaseDate() != null) {
            asset.setPurchaseDate(assetDTO.getPurchaseDate());
        }
        if (assetDTO.getVendor() != null) {
            asset.setVendor(assetDTO.getVendor());
        }
        if (assetDTO.getCost() != null) {
            asset.setCost(assetDTO.getCost());
        }

        Asset updatedAsset = assetRepository.save(asset);
        log.info("Asset updated successfully with ID: {}", id);

        return AssetDTO.fromEntity(updatedAsset);
    }

    /**
     * Delete asset
     */
    public void deleteAsset(Long id) {
        log.info("Deleting asset with ID: {}", id);

        if (!assetRepository.existsById(id)) {
            throw new AssetNotFoundException("Asset not found with ID: " + id);
        }

        assetRepository.deleteById(id);
        log.info("Asset deleted successfully with ID: {}", id);
    }

    /**
     * Search assets
     */
    @Transactional(readOnly = true)
    public List<AssetDTO> searchAssets(String query) {
        log.info("Searching assets with query: {}", query);
        return assetRepository.searchAssets(query).stream()
            .map(AssetDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Get assets by status
     */
    @Transactional(readOnly = true)
    public List<AssetDTO> getAssetsByStatus(String status) {
        log.info("Fetching assets with status: {}", status);
        return assetRepository.findByStatus(Asset.AssetStatus.valueOf(status)).stream()
            .map(AssetDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Get assets by type
     */
    @Transactional(readOnly = true)
    public List<AssetDTO> getAssetsByType(String assetType) {
        log.info("Fetching assets with type: {}", assetType);
        return assetRepository.findByAssetType(assetType).stream()
            .map(AssetDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Get assets assigned to user
     */
    @Transactional(readOnly = true)
    public List<AssetDTO> getAssetsByAssignedTo(Long userId) {
        log.info("Fetching assets assigned to user: {}", userId);
        return assetRepository.findByAssignedTo(userId).stream()
            .map(AssetDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Get assets by location
     */
    @Transactional(readOnly = true)
    public List<AssetDTO> getAssetsByLocation(String location) {
        log.info("Fetching assets at location: {}", location);
        return assetRepository.findByLocation(location).stream()
            .map(AssetDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Get asset by serial number
     */
    @Transactional(readOnly = true)
    public AssetDTO getAssetBySerialNumber(String serialNumber) {
        log.info("Fetching asset with serial number: {}", serialNumber);
        Asset asset = assetRepository.findBySerialNumber(serialNumber)
            .orElseThrow(() -> new AssetNotFoundException("Asset not found with serial number: " + serialNumber));
        return AssetDTO.fromEntity(asset);
    }
}
