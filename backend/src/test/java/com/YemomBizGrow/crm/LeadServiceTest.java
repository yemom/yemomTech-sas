package com.YemomBizGrow.crm;

import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.crm.dto.CreateLeadRequest;
import com.YemomBizGrow.crm.dto.LeadResponse;
import com.YemomBizGrow.crm.dto.PipelineSummaryResponse;
import com.YemomBizGrow.crm.dto.PublicLeadCaptureRequest;
import com.YemomBizGrow.crm.dto.TransitionStageRequest;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.organization.OrganizationRepository;
import com.YemomBizGrow.organization.OrganizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadActivityRepository activityRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private AuditService auditService;

    private LeadService leadService;

    private final UUID actorId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        leadService = new LeadService(
                leadRepository,
                activityRepository,
                organizationRepository,
                accessControlService,
                auditService
        );
    }

    @Test
    @DisplayName("Creates a lead and records creation activity and audit log")
    void createLeadSuccess() {
        CreateLeadRequest request = new CreateLeadRequest(
                "Abebe", "Bikila", "abebe@example.et", "+251911223344",
                "Addis Marathon", LeadSource.DIRECT, LeadStage.NEW, null,
                new BigDecimal("50000.00"), "Interested in annual sponsorship"
        );

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> {
            Lead l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        LeadResponse response = leadService.createLead(actorId, orgId, request);

        assertThat(response.firstName()).isEqualTo("Abebe");
        assertThat(response.lastName()).isEqualTo("Bikila");
        assertThat(response.email()).isEqualTo("abebe@example.et");
        assertThat(response.stage()).isEqualTo(LeadStage.NEW);
        assertThat(response.estimatedValueEtb()).isEqualByComparingTo("50000.00");

        verify(accessControlService).requirePermission(actorId, orgId, Permission.LEAD_MANAGE);
        verify(activityRepository).save(any(LeadActivity.class));
        verify(auditService).record(eq(actorId), eq(orgId), eq(AuditAction.LEAD_CREATED), eq("Lead"), any(UUID.class), any());
    }

    @Test
    @DisplayName("Throws NotFoundException when lead is not found")
    void getLeadNotFound() {
        UUID leadId = UUID.randomUUID();
        when(leadRepository.findByIdAndOrganizationId(leadId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leadService.getLead(actorId, orgId, leadId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Lead not found");
    }

    @Test
    @DisplayName("Transitions stage, recording stage change activity and audit log")
    void transitionStageSuccess() {
        UUID leadId = UUID.randomUUID();
        Lead lead = new Lead();
        lead.setId(leadId);
        lead.setOrganizationId(orgId);
        lead.setFirstName("Almaz");
        lead.setStage(LeadStage.NEW);

        when(leadRepository.findByIdAndOrganizationId(leadId, orgId)).thenReturn(Optional.of(lead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        TransitionStageRequest request = new TransitionStageRequest(LeadStage.CONTACTED, "Initial discovery call held");
        LeadResponse result = leadService.transitionStage(actorId, orgId, leadId, request);

        assertThat(result.stage()).isEqualTo(LeadStage.CONTACTED);

        ArgumentCaptor<LeadActivity> activityCaptor = ArgumentCaptor.forClass(LeadActivity.class);
        verify(activityRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getActivityType()).isEqualTo(LeadActivityType.STAGE_CHANGE);
        assertThat(activityCaptor.getValue().getDescription()).contains("NEW to CONTACTED");

        verify(auditService).record(eq(actorId), eq(orgId), eq(AuditAction.LEAD_STAGE_CHANGED), eq("Lead"), eq(leadId), any());
    }

    @Test
    @DisplayName("Drops honeypot bot submissions without creating a lead")
    void publicCaptureHoneypotSpamDropped() {
        PublicLeadCaptureRequest request = new PublicLeadCaptureRequest(
                "BotName", "Spammer", "bot@spam.com", null, null, null,
                "https://spam-link.com" // honeypot filled!
        );

        leadService.capturePublicLead("some-org", request, "192.0.2.1");

        verify(organizationRepository, never()).findBySlug(any());
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("Captures legitimate public lead from website form")
    void publicCaptureLegitimateLead() {
        String slug = "yemom-consulting";
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setSlug(slug);
        org.setStatus(OrganizationStatus.ACTIVE);

        when(organizationRepository.findBySlug(slug)).thenReturn(Optional.of(org));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> {
            Lead l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        PublicLeadCaptureRequest request = new PublicLeadCaptureRequest(
                "Kebede", "Lemma", "kebede@example.et", "+251922334455",
                "Lemma Trading", "We need an ERP and growth strategy.", null
        );

        leadService.capturePublicLead(slug, request, "197.156.120.1");

        ArgumentCaptor<Lead> leadCaptor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(leadCaptor.capture());
        assertThat(leadCaptor.getValue().getFirstName()).isEqualTo("Kebede");
        assertThat(leadCaptor.getValue().getSource()).isEqualTo(LeadSource.WEBSITE_FORM);
        assertThat(leadCaptor.getValue().getStage()).isEqualTo(LeadStage.NEW);

        verify(activityRepository).save(any(LeadActivity.class));
        verify(auditService).record(eq(null), eq(org.getId()), eq(AuditAction.LEAD_CREATED), eq("Lead"), any(UUID.class), any());
    }

    @Test
    @DisplayName("Archives lead setting status to ARCHIVED")
    void archiveLeadSuccess() {
        UUID leadId = UUID.randomUUID();
        Lead lead = new Lead();
        lead.setId(leadId);
        lead.setOrganizationId(orgId);
        lead.setStatus(LeadStatus.ACTIVE);

        when(leadRepository.findByIdAndOrganizationId(leadId, orgId)).thenReturn(Optional.of(lead));

        leadService.archiveLead(actorId, orgId, leadId);

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.ARCHIVED);
        verify(leadRepository).save(lead);
        verify(auditService).record(eq(actorId), eq(orgId), eq(AuditAction.LEAD_ARCHIVED), eq("Lead"), eq(leadId), any());
    }

    @Test
    @DisplayName("Aggregates pipeline summary across stages")
    void getPipelineSummarySuccess() {
        LeadRepository.StageSummaryView viewNew = new LeadRepository.StageSummaryView() {
            @Override public LeadStage getStage() { return LeadStage.NEW; }
            @Override public long getCount() { return 5; }
            @Override public BigDecimal getTotalValue() { return new BigDecimal("100000.00"); }
        };
        LeadRepository.StageSummaryView viewWon = new LeadRepository.StageSummaryView() {
            @Override public LeadStage getStage() { return LeadStage.WON; }
            @Override public long getCount() { return 2; }
            @Override public BigDecimal getTotalValue() { return new BigDecimal("250000.00"); }
        };

        when(leadRepository.getPipelineSummary(orgId)).thenReturn(List.of(viewNew, viewWon));

        PipelineSummaryResponse summary = leadService.getPipelineSummary(actorId, orgId);

        assertThat(summary.totalLeads()).isEqualTo(7);
        assertThat(summary.totalPipelineValueEtb()).isEqualByComparingTo("350000.00");
        assertThat(summary.stages().get(LeadStage.NEW).count()).isEqualTo(5);
        assertThat(summary.stages().get(LeadStage.WON).count()).isEqualTo(2);
        assertThat(summary.stages().get(LeadStage.LOST).count()).isEqualTo(0);
    }

    @Test
    @DisplayName("capturePublicLead throws TooManyRequestsException when IP rate limit is exceeded")
    void capturePublicLeadRateLimitExceeded() {
        org.springframework.data.redis.core.StringRedisTemplate mockRedis = org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        when(mockRedis.execute(any(), any(), any())).thenReturn(15L); // > 10 attempts

        LeadService rateLimitedService = new LeadService(
                leadRepository, activityRepository, organizationRepository, accessControlService, auditService, mockRedis
        );

        PublicLeadCaptureRequest request = new PublicLeadCaptureRequest(
                "Abebe", "Kebede", "abebe@example.et", "+251911223344", null, "Hello", null
        );

        assertThatThrownBy(() -> rateLimitedService.capturePublicLead("some-slug", request, "196.188.1.1"))
                .isInstanceOf(com.YemomBizGrow.common.exception.TooManyRequestsException.class)
                .hasMessageContaining("Too many submission attempts");
    }

    @Test
    @DisplayName("capturePublicLead falls back to in-memory rate limiting when Redis is unavailable")
    void capturePublicLead_fallbackToInMemoryRateLimit() {
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName("Org");
        org.setSlug("test-fallback-slug");
        when(organizationRepository.findBySlug("test-fallback-slug")).thenReturn(Optional.of(org));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> {
            Lead l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        LeadService fallbackService = new LeadService(
                leadRepository, activityRepository, organizationRepository, accessControlService, auditService, null
        );

        PublicLeadCaptureRequest request = new PublicLeadCaptureRequest(
                "Abebe", "Kebede", "abebe@example.et", "+251911223344", null, "Hello", null
        );

        String testIp = "203.0.113.99";
        // 10 successful requests allowed
        for (int i = 0; i < 10; i++) {
            fallbackService.capturePublicLead("test-fallback-slug", request, testIp);
        }

        // 11th request must be blocked by the in-memory fallback rate limiter
        assertThatThrownBy(() -> fallbackService.capturePublicLead("test-fallback-slug", request, testIp))
                .isInstanceOf(com.YemomBizGrow.common.exception.TooManyRequestsException.class)
                .hasMessageContaining("Too many submission attempts");
    }
}
