package com.helpdesk.asset.controller;

import com.helpdesk.asset.dto.AssetDTO;
import com.helpdesk.asset.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Asset Controller - REST API endpoints for asset management
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Assets", description = "Asset management endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class AssetController {

    private final AssetService assetService;

    /**
     * Create a new asset
     */
    @PostMapping
    @Operation(summary = "Create a new asset", description = "Create a new IT asset")
    public ResponseEntity<AssetDTO> createAsset(@RequestBody AssetDTO assetDTO) {
        log.info("REST request to create asset");
        AssetDTO createdAsset = assetService.createAsset(assetDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAsset);
    }

    /**
     * Get all assets
     */
    @GetMapping
    @Operation(summary = "Get all assets", description = "Retrieve all IT assets")
    public ResponseEntity<List<AssetDTO>> getAllAssets() {
        log.info("REST request to get all assets");
        List<AssetDTO> assets = assetService.getAllAssets();
        return ResponseEntity.ok(assets);
    }

    /**
     * Get asset by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get asset by ID", description = "Retrieve a specific asset by its ID")
    public ResponseEntity<AssetDTO> getAssetById(@PathVariable Long id) {
        log.info("REST request to get asset with ID: {}", id);
        AssetDTO asset = assetService.getAssetById(id);
        return ResponseEntity.ok(asset);
    }

    /**
     * Update asset
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update asset", description = "Update an existing asset")
    public ResponseEntity<AssetDTO> updateAsset(@PathVariable Long id, @RequestBody AssetDTO assetDTO) {
        log.info("REST request to update asset with ID: {}", id);
        AssetDTO updatedAsset = assetService.updateAsset(id, assetDTO);
        return ResponseEntity.ok(updatedAsset);
    }

    /**
     * Delete asset
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete asset", description = "Delete an asset by its ID")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long id) {
        log.info("REST request to delete asset with ID: {}", id);
        assetService.deleteAsset(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Search assets
     */
    @GetMapping("/search")
    @Operation(summary = "Search assets", description = "Search assets by name, description, or serial number")
    public ResponseEntity<List<AssetDTO>> searchAssets(@RequestParam String query) {
        log.info("REST request to search assets with query: {}", query);
        List<AssetDTO> assets = assetService.searchAssets(query);
        return ResponseEntity.ok(assets);
    }

    /**
     * Get assets by status
     */
    @GetMapping("/by-status/{status}")
    @Operation(summary = "Get assets by status", description = "Retrieve assets filtered by status")
    public ResponseEntity<List<AssetDTO>> getAssetsByStatus(@PathVariable String status) {
        log.info("REST request to get assets by status: {}", status);
        List<AssetDTO> assets = assetService.getAssetsByStatus(status);
        return ResponseEntity.ok(assets);
    }

    /**
     * Get assets by type
     */
    @GetMapping("/by-type/{assetType}")
    @Operation(summary = "Get assets by type", description = "Retrieve assets filtered by type")
    public ResponseEntity<List<AssetDTO>> getAssetsByType(@PathVariable String assetType) {
        log.info("REST request to get assets by type: {}", assetType);
        List<AssetDTO> assets = assetService.getAssetsByType(assetType);
        return ResponseEntity.ok(assets);
    }

    /**
     * Get assets assigned to user
     */
    @GetMapping("/assigned-to/{userId}")
    @Operation(summary = "Get assets assigned to user", description = "Retrieve assets assigned to a specific user")
    public ResponseEntity<List<AssetDTO>> getAssetsByAssignedTo(@PathVariable Long userId) {
        log.info("REST request to get assets assigned to user: {}", userId);
        List<AssetDTO> assets = assetService.getAssetsByAssignedTo(userId);
        return ResponseEntity.ok(assets);
    }

    /**
     * Get assets by location
     */
    @GetMapping("/by-location/{location}")
    @Operation(summary = "Get assets by location", description = "Retrieve assets filtered by location")
    public ResponseEntity<List<AssetDTO>> getAssetsByLocation(@PathVariable String location) {
        log.info("REST request to get assets by location: {}", location);
        List<AssetDTO> assets = assetService.getAssetsByLocation(location);
        return ResponseEntity.ok(assets);
    }

    /**
     * Get asset by serial number
     */
    @GetMapping("/by-serial/{serialNumber}")
    @Operation(summary = "Get asset by serial number", description = "Retrieve asset by its serial number")
    public ResponseEntity<AssetDTO> getAssetBySerialNumber(@PathVariable String serialNumber) {
        log.info("REST request to get asset by serial number: {}", serialNumber);
        AssetDTO asset = assetService.getAssetBySerialNumber(serialNumber);
        return ResponseEntity.ok(asset);
    }
}
