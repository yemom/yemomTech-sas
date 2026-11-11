package com.YemomBizGrow.crm;

import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.common.exception.TooManyRequestsException;
import com.YemomBizGrow.common.response.PageResponse;
import com.YemomBizGrow.common.util.TokenHasher;
import com.YemomBizGrow.crm.dto.AddActivityRequest;
import com.YemomBizGrow.crm.dto.CreateLeadRequest;
import com.YemomBizGrow.crm.dto.LeadActivityResponse;
import com.YemomBizGrow.crm.dto.LeadResponse;
import com.YemomBizGrow.crm.dto.PipelineSummaryResponse;
import com.YemomBizGrow.crm.dto.PublicLeadCaptureRequest;
import com.YemomBizGrow.crm.dto.TransitionStageRequest;
import com.YemomBizGrow.crm.dto.UpdateLeadRequest;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.organization.OrganizationRepository;
import com.YemomBizGrow.organization.OrganizationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);

    private static final RedisScript<Long> INCR_EXPIRE_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current == 1 then " +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return current",
            Long.class
    );

    private final LeadRepository leadRepository;
    private final LeadActivityRepository activityRepository;
    private final OrganizationRepository organizationRepository;
    private final AccessControlService accessControlService;
    private final AuditService auditService;
    private final StringRedisTemplate redisTemplate;

    public LeadService(
            LeadRepository leadRepository,
            LeadActivityRepository activityRepository,
            OrganizationRepository organizationRepository,
            AccessControlService accessControlService,
            AuditService auditService) {
        this(leadRepository, activityRepository, organizationRepository, accessControlService, auditService, null);
    }

    @Autowired
    public LeadService(
            LeadRepository leadRepository,
            LeadActivityRepository activityRepository,
            OrganizationRepository organizationRepository,
            AccessControlService accessControlService,
            AuditService auditService,
            @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.leadRepository = leadRepository;
        this.activityRepository = activityRepository;
        this.organizationRepository = organizationRepository;
        this.accessControlService = accessControlService;
        this.auditService = auditService;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public LeadResponse createLead(UUID actorUserId, UUID organizationId, CreateLeadRequest request) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.LEAD_MANAGE);

        Lead lead = new Lead();
        lead.setOrganizationId(organizationId);
        lead.setFirstName(request.firstName().trim());
        lead.setLastName(request.lastName() != null ? request.lastName().trim() : null);
        lead.setEmail(request.email() != null ? request.email().trim().toLowerCase() : null);
        String phone = request.phoneNumber() != null ? request.phoneNumber().trim() : null;
        if (phone != null) {
            String normalized = com.YemomBizGrow.common.util.EthiopianPhoneNormalizer.normalize(phone);
            lead.setPhoneNumber(normalized != null ? normalized : phone);
        } else {
            lead.setPhoneNumber(null);
        }
        lead.setCompanyName(request.companyName() != null ? request.companyName().trim() : null);
        lead.setSource(request.source() != null ? request.source() : LeadSource.DIRECT);
        lead.setStage(request.stage() != null ? request.stage() : LeadStage.NEW);
        lead.setStatus(LeadStatus.ACTIVE);
        lead.setAssignedUserId(request.assignedUserId());
        lead.setCampaignId(request.campaignId());
        lead.setEstimatedValueEtb(request.estimatedValueEtb());
        lead.setNotes(request.notes());

        lead = leadRepository.save(lead);

        // Record creation activity
        LeadActivity activity = new LeadActivity();
        activity.setOrganizationId(organizationId);
        activity.setLeadId(lead.getId());
        activity.setActorUserId(actorUserId);
        activity.setActivityType(LeadActivityType.NOTE);
        activity.setDescription("Lead created manually by team member");
        activityRepository.save(activity);

        auditService.record(actorUserId, organizationId, AuditAction.LEAD_CREATED,
                "Lead", lead.getId(),
                Map.of("firstName", lead.getFirstName(), "stage", lead.getStage().name()));

        return LeadResponse.from(lead);
    }

    @Transactional(readOnly = true)
    public LeadResponse getLead(UUID actorUserId, UUID organizationId, UUID leadId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.LEAD_MANAGE);

        Lead lead = leadRepository.findByIdAndOrganizationId(leadId, organizationId)
                .orElseThrow(() -> new NotFoundException("Lead not found."));

        return LeadResponse.from(lead);
    }

    @Transactional(readOnly = true)
    public PageResponse<LeadResponse> listLeads(
            UUID actorUserId, UUID organizationId, LeadStage stage, String search, Pageable pageable) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.LEAD_MANAGE);

        Page<Lead> page;
        if (search != null && !search.isBlank()) {
            page = leadRepository.searchLeads(organizationId, search.trim(), pageable);
        } else if (stage != null) {
            page = leadRepository.findByOrganizationIdAndStatusAndStageOrderByCreatedAtDesc(
                    organizationId, LeadStatus.ACTIVE, stage, pageable);
        } else {
            page = leadRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                    organizationId, LeadStatus.ACTIVE, pageable);
        }

        return PageResponse.from(page.map(LeadResponse::from));
    }

    @Transactional
    public LeadResponse updateLead(UUID actorUserId, UUID organizationId, UUID leadId, UpdateLeadRequest request) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.LEAD_MANAGE);

        Lead lead = leadRepository.findByIdAndOrganizationId(leadId, organizationId)
                .orElseThrow(() -> new NotFoundException("Lead not found."));

        if (request.firstName() != null && !request.firstName().isBlank()) {
            lead.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) {
            lead.setLastName(request.lastName().trim());
        }
        if (request.email() != null) {
            lead.setEmail(request.email().trim().toLowerCase());
        }
        if (request.phoneNumber() != null) {
            String p = request.phoneNumber().trim();
            String normalized = com.YemomBizGrow.common.util.EthiopianPhoneNormalizer.normalize(p);
            lead.setPhoneNumber(normalized != null ? normalized : p);
        }
        if (request.companyName() != null) {
            lead.setCompanyName(request.companyName().trim());
        }
        if (request.assignedUserId() != null) {
            lead.setAssignedUserId(request.assignedUserId());
        }
        if (request.estimatedValueEtb() != null) {
            lead.setEstimatedValueEtb(request.estimatedValueEtb());
        }
        if (request.notes() != null) {
            lead.setNotes(request.notes());
        }

        lead = leadRepository.save(lead);

        auditService.record(actorUserId, organizationId, AuditAction.LEAD_UPDATED,
                "Lead", lead.getId(), Map.of("leadId", lead.getId()));

        return LeadResponse.from(lead);
    }

    @Transactional
    public LeadResponse transitionStage(
            UUID actorUserId, UUID organizationId, UUID leadId, TransitionStageRequest request) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.LEAD_MANAGE);

        Lead lead = leadRepository.findByIdAndOrganizationId(leadId, organizationId)
                .orElseThrow(() -> new NotFoundException("Lead not found."));

        LeadStage oldStage = lead.getStage();
        LeadStage newStage = request.stage();

        if (oldStage == newStage) {
            return LeadResponse.from(lead);
        }

        lead.setStage(newStage);
        lead = leadRepository.save(lead);

        // Record stage change activity
        LeadActivity activity = new LeadActivity();
        activity.setOrganizationId(organizationId);
        activity.setLeadId(lead.getId());
        activity.setActorUserId(actorUserId);
        activity.setActivityType(LeadActivityType.STAGE_CHANGE);
        activity.setDescription("Stage transitioned from " + oldStage + " to " + newStage);

        Map<String, Object> meta = new HashMap<>();
        meta.put("fromStage", oldStage.name());
        meta.put("toStage", newStage.name());
        if (request.notes() != null && !request.notes().isBlank()) {
            meta.put("notes", request.notes());
        }
        activity.setMetadata(meta);
        activityRepository.save(activity);

        auditService.record(actorUserId, organizationId, AuditAction.LEAD_STAGE_CHANGED,
                "Lead", lead.getId(),
                Map.of("from", oldStage.name(), "to", newStage.name()));

        return LeadResponse.from(lead);
    }

    @Transactional
    public void archiveLead(UUID actorUserId, UUID organizationId, UUID leadId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.LEAD_MANAGE);

        Lead lead = leadRepository.findByIdAndOrganizationId(leadId, organizationId)
                .orElseThrow(() -> new NotFoundException("Lead not found."));

        lead.setStatus(LeadStatus.ARCHIVED);
        leadRepository.save(lead);

        auditService.record(actorUserId, organizationId, AuditAction.LEAD_ARCHIVED,
                "Lead", lead.getId(), Map.of("leadId", lead.getId()));
    }

    @Transactional
    public LeadActivityResponse addActivity(
            UUID actorUserId, UUID organizationId, UUID leadId, AddActivityRequest request) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.LEAD_MANAGE);

        Lead lead = leadRepository.findByIdAndOrganizationId(leadId, organizationId)
                .orElseThrow(() -> new NotFoundException("Lead not found."));

        LeadActivity activity = new LeadActivity();
        activity.setOrganizationId(organizationId);
        activity.setLeadId(lead.getId());
        activity.setActorUserId(actorUserId);
        activity.setActivityType(request.activityType());
        activity.setDescription(request.description());
        activity.setMetadata(request.metadata());

        activity = activityRepository.save(activity);
        return LeadActivityResponse.from(activity);
    }

    @Transactional(readOnly = true)
    public PageResponse<LeadActivityResponse> listActivities(
            UUID actorUserId, UUID organizationId, UUID leadId, Pageable pageable) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.LEAD_MANAGE);

        // Verify lead exists in org
        leadRepository.findByIdAndOrganizationId(leadId, organizationId)
                .orElseThrow(() -> new NotFoundException("Lead not found."));

        Page<LeadActivity> page = activityRepository.findByOrganizationIdAndLeadIdOrderByCreatedAtDesc(
                organizationId, leadId, pageable);
        return PageResponse.from(page.map(LeadActivityResponse::from));
    }

    @Transactional(readOnly = true)
    public PipelineSummaryResponse getPipelineSummary(UUID actorUserId, UUID organizationId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.LEAD_MANAGE);

        List<LeadRepository.StageSummaryView> views = leadRepository.getPipelineSummary(organizationId);

        Map<LeadStage, PipelineSummaryResponse.StageMetrics> metricsMap = new EnumMap<>(LeadStage.class);
        // Initialize all stages with 0
        for (LeadStage stage : LeadStage.values()) {
            metricsMap.put(stage, new PipelineSummaryResponse.StageMetrics(0, BigDecimal.ZERO));
        }

        long totalCount = 0;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (LeadRepository.StageSummaryView view : views) {
            metricsMap.put(view.getStage(), new PipelineSummaryResponse.StageMetrics(view.getCount(), view.getTotalValue()));
            totalCount += view.getCount();
            totalValue = totalValue.add(view.getTotalValue());
        }

        return new PipelineSummaryResponse(metricsMap, totalCount, totalValue);
    }

    @Transactional
    public void capturePublicLead(String orgSlug, PublicLeadCaptureRequest request, String clientIp) {
        // Honeypot check: automated spambots fill all inputs
        if (request.honeypot() != null && !request.honeypot().isBlank()) {
            log.warn("Public lead capture dropped honeypot spam submission from IP: {}", clientIp);
            return; // Silently drop without revealing error to bot
        }

        // Rate limiting: 10 submissions per 15 minutes per IP
        enforceLeadRateLimit(clientIp);

        Organization organization = organizationRepository.findBySlug(orgSlug)
                .orElseThrow(() -> new NotFoundException("Organization not found."));

        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new NotFoundException("Organization not found.");
        }

        Lead lead = new Lead();
        lead.setOrganizationId(organization.getId());
        lead.setFirstName(request.firstName().trim());
        lead.setLastName(request.lastName() != null ? request.lastName().trim() : null);
        lead.setEmail(request.email() != null ? request.email().trim().toLowerCase() : null);
        String phone = request.phoneNumber() != null ? request.phoneNumber().trim() : null;
        if (phone != null) {
            String normalized = com.YemomBizGrow.common.util.EthiopianPhoneNormalizer.normalize(phone);
            lead.setPhoneNumber(normalized != null ? normalized : phone);
        } else {
            lead.setPhoneNumber(null);
        }
        lead.setCompanyName(request.companyName() != null ? request.companyName().trim() : null);
        lead.setSource(LeadSource.WEBSITE_FORM);
        lead.setStage(LeadStage.NEW);
        lead.setStatus(LeadStatus.ACTIVE);
        lead.setNotes(request.notes());

        lead = leadRepository.save(lead);

        LeadActivity activity = new LeadActivity();
        activity.setOrganizationId(organization.getId());
        activity.setLeadId(lead.getId());
        activity.setActivityType(LeadActivityType.FORM_SUBMISSION);
        activity.setDescription("Lead captured via public website form submission");
        activity.setMetadata(Map.of("clientIp", clientIp != null ? clientIp : "unknown"));
        activityRepository.save(activity);

        auditService.record(null, organization.getId(), AuditAction.LEAD_CREATED,
                "Lead", lead.getId(),
                Map.of("source", "WEBSITE_FORM", "stage", "NEW"));

        log.info("Public lead captured for org slug '{}' with lead ID {}", orgSlug, lead.getId());
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, long[]> FALLBACK_RATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int RATE_LIMIT_MAX_REQUESTS = 10;
    private static final long RATE_LIMIT_WINDOW_MILLIS = 15 * 60 * 1000L; // 15 minutes

    private void enforceLeadRateLimit(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        if (redisTemplate != null) {
            try {
                String key = "lead:capture:ip:" + TokenHasher.sha256Hex(clientIp);
                Long count = redisTemplate.execute(
                        INCR_EXPIRE_SCRIPT,
                        Collections.singletonList(key),
                        String.valueOf(900L) // 15 minutes window
                );
                if (count != null && count > RATE_LIMIT_MAX_REQUESTS) {
                    log.warn("Public lead submission rate limit exceeded for IP {}", clientIp);
                    throw new TooManyRequestsException("Too many submission attempts from your network. Please try again in 15 minutes.");
                }
                return;
            } catch (TooManyRequestsException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Redis lead rate limit failed, falling back to in-memory rate limiter: {}", e.getMessage());
            }
        }

        enforceInMemoryRateLimit(clientIp);
    }

    private void enforceInMemoryRateLimit(String clientIp) {
        long now = System.currentTimeMillis();
        if (FALLBACK_RATE_CACHE.size() > 10000) {
            FALLBACK_RATE_CACHE.entrySet().removeIf(entry -> now - entry.getValue()[1] > RATE_LIMIT_WINDOW_MILLIS);
        }

        long[] stats = FALLBACK_RATE_CACHE.compute(clientIp, (ip, current) -> {
            if (current == null || now - current[1] > RATE_LIMIT_WINDOW_MILLIS) {
                return new long[]{1L, now};
            }
            current[0]++;
            return current;
        });

        if (stats[0] > RATE_LIMIT_MAX_REQUESTS) {
            log.warn("In-memory public lead submission rate limit exceeded for IP {}", clientIp);
            throw new TooManyRequestsException("Too many submission attempts from your network. Please try again in 15 minutes.");
        }
    }
}
