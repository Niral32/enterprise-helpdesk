package com.helpdesk.asset.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Asset Entity - IT Assets management
 */
@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String assetType;

    @Column(nullable = false, unique = true)
    private String serialNumber;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(32)")
    private AssetStatus status;

    @Column(nullable = false)
    private Long assignedTo;

    @Column
    private String location;

    @Column
    private LocalDateTime purchaseDate;

    @Column
    private String vendor;

    @Column
    private Double cost;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum AssetStatus {
        AVAILABLE, ASSIGNED, IN_REPAIR, RETIRED, LOST
    }
}
