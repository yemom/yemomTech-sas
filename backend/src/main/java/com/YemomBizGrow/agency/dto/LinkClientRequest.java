package com.YemomBizGrow.agency.dto;

import com.YemomBizGrow.agency.AgencyAccessLevel;
import jakarta.validation.constraints.NotBlank;

public record LinkClientRequest(
        @NotBlank(message = "Client organization slug is required")
        String clientOrganizationSlug,

        AgencyAccessLevel accessLevel,

        String notes
) {}
