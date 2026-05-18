package com.helpdesk.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body for {@code PATCH /api/tickets/{id}/link-asset}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkAssetRequest {
    /** ID of the asset (in asset-service) to link this ticket to. Null to unlink. */
    private Long assetId;
}
