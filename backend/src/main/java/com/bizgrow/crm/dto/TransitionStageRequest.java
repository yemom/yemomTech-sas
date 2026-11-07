package com.YemomBizGrow.crm.dto;

import com.YemomBizGrow.crm.LeadStage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransitionStageRequest(
        @NotNull(message = "Target stage is required")
        LeadStage stage,

        @Size(max = 2000, message = "Notes must not exceed 2000 characters")
        String notes
) {}
