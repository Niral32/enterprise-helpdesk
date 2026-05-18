package com.helpdesk.asset.dto;

import com.helpdesk.asset.entity.Asset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Asset DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetDTO {
    private Long id;
    private String name;
    private String assetType;
    private String serialNumber;
    private String description;
    private String status;
    private Long assignedTo;
    private String location;
    private LocalDateTime purchaseDate;
    private String vendor;
    private Double cost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AssetDTO fromEntity(Asset asset) {
        return AssetDTO.builder()
            .id(asset.getId())
            .name(asset.getName())
            .assetType(asset.getAssetType())
            .serialNumber(asset.getSerialNumber())
            .description(asset.getDescription())
            .status(asset.getStatus().toString())
            .assignedTo(asset.getAssignedTo())
            .location(asset.getLocation())
            .purchaseDate(asset.getPurchaseDate())
            .vendor(asset.getVendor())
            .cost(asset.getCost())
            .createdAt(asset.getCreatedAt())
            .updatedAt(asset.getUpdatedAt())
            .build();
    }
}
