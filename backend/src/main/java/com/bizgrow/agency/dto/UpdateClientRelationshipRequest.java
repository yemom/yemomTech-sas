package com.YemomBizGrow.agency.dto;

import com.YemomBizGrow.agency.AgencyAccessLevel;
import com.YemomBizGrow.agency.AgencyRelationshipStatus;

public record UpdateClientRelationshipRequest(
        AgencyRelationshipStatus status,
        AgencyAccessLevel accessLevel,
        String notes
) {}
