package com.helpdesk.asset.repository;

import com.helpdesk.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Asset Repository
 */
@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    /**
     * Find asset by serial number
     */
    Optional<Asset> findBySerialNumber(String serialNumber);

    /**
     * Find assets by status
     */
    List<Asset> findByStatus(Asset.AssetStatus status);

    /**
     * Find assets by asset type
     */
    List<Asset> findByAssetType(String assetType);

    /**
     * Find assets assigned to user
     */
    List<Asset> findByAssignedTo(Long userId);

    /**
     * Find assets by location
     */
    List<Asset> findByLocation(String location);

    /**
     * Search assets by name, description or serial number
     */
    @Query("SELECT a FROM Asset a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Asset> searchAssets(@Param("query") String query);
}
