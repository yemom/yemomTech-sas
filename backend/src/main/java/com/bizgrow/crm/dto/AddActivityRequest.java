package com.YemomBizGrow.crm.dto;

import com.YemomBizGrow.crm.LeadActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record AddActivityRequest(
        @NotNull(message = "Activity type is required")
        LeadActivityType activityType,

        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        Map<String, Object> metadata
) {}
