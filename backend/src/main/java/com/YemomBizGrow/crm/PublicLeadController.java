package com.YemomBizGrow.crm;

import com.YemomBizGrow.common.response.MessageResponse;
import com.YemomBizGrow.common.security.ClientIpResolver;
import com.YemomBizGrow.crm.dto.PublicLeadCaptureRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/leads")
@Tag(name = "Public Leads", description = "Public website lead capture endpoints with spam protection")
public class PublicLeadController {

    private final LeadService leadService;
    private final ClientIpResolver clientIpResolver;

    public PublicLeadController(LeadService leadService, ClientIpResolver clientIpResolver) {
        this.leadService = leadService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/{orgSlug}")
    public ResponseEntity<MessageResponse> captureLead(
            @PathVariable String orgSlug,
            @Valid @RequestBody PublicLeadCaptureRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = clientIpResolver.resolveClientIp(servletRequest);
        leadService.capturePublicLead(orgSlug, request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Thank you! Your message has been received."));
    }
}
