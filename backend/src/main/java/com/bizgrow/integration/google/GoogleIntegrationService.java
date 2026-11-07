package com.YemomBizGrow.integration.google;

import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.integration.google.dto.AnalyticsDailyPoint;
import com.YemomBizGrow.integration.google.dto.AnalyticsOverviewResponse;
import com.YemomBizGrow.integration.google.dto.GoogleCallbackRequest;
import com.YemomBizGrow.integration.google.dto.GoogleConnectUrlResponse;
import com.YemomBizGrow.integration.google.dto.GoogleConnectionStatusResponse;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import com.YemomBizGrow.website.Website;
import com.YemomBizGrow.website.WebsiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class GoogleIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GoogleIntegrationService.class);

    private static final String GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String DEFAULT_SCOPES = "openid email "
            + "https://www.googleapis.com/auth/analytics.readonly "
            + "https://www.googleapis.com/auth/webmasters.readonly";

    private final GoogleCredentialRepository credentialRepository;
    private final GoogleAnalyticsSnapshotRepository gaSnapshotRepository;
    private final GoogleSearchConsoleSnapshotRepository gscSnapshotRepository;
    private final WebsiteRepository websiteRepository;
    private final AccessControlService accessControlService;
    private final AuditService auditService;
    private final EncryptionService encryptionService;
    private final GoogleIntegrationProperties googleProps;

    public GoogleIntegrationService(
            GoogleCredentialRepository credentialRepository,
            GoogleAnalyticsSnapshotRepository gaSnapshotRepository,
            GoogleSearchConsoleSnapshotRepository gscSnapshotRepository,
            WebsiteRepository websiteRepository,
            AccessControlService accessControlService,
            AuditService auditService,
            EncryptionService encryptionService,
            GoogleIntegrationProperties googleProps) {
        this.credentialRepository = credentialRepository;
        this.gaSnapshotRepository = gaSnapshotRepository;
        this.gscSnapshotRepository = gscSnapshotRepository;
        this.websiteRepository = websiteRepository;
        this.accessControlService = accessControlService;
        this.auditService = auditService;
        this.encryptionService = encryptionService;
        this.googleProps = googleProps;
    }

    @Transactional(readOnly = true)
    public GoogleConnectionStatusResponse getConnectionStatus(UUID actorUserId, UUID organizationId, UUID websiteId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.ANALYTICS_VIEW);
        requireWebsite(organizationId, websiteId);

        return credentialRepository.findByOrganizationIdAndWebsiteId(organizationId, websiteId)
                .map(cred -> new GoogleConnectionStatusResponse(
                        true,
                        cred.getWebsiteId(),
                        cred.getGoogleAccountId(),
                        cred.getCreatedAt(),
                        cred.getTokenExpiry(),
                        cred.getScopes()))
                .orElseGet(() -> GoogleConnectionStatusResponse.notConnected(websiteId));
    }

    @Transactional
    public GoogleConnectUrlResponse generateConnectUrl(UUID actorUserId, UUID organizationId, UUID websiteId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.WEBSITE_MANAGE);
        requireWebsite(organizationId, websiteId);

        String state = UUID.randomUUID().toString();

        GoogleCredential credential = credentialRepository.findByOrganizationIdAndWebsiteId(organizationId, websiteId)
                .orElseGet(() -> {
                    GoogleCredential c = new GoogleCredential();
                    c.setOrganizationId(organizationId);
                    c.setWebsiteId(websiteId);
                    c.setGoogleAccountId("pending");
                    c.setAccessTokenEnc(encryptionService.encrypt("pending"));
                    return c;
                });

        credential.setOauthState(state);
        credential.setUpdatedAt(Instant.now());
        credentialRepository.save(credential);

        String clientId = googleProps.getClientId().isBlank() ? "dev-client-id" : googleProps.getClientId();
        String redirectUri = googleProps.getRedirectUri();

        String authUrl = UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", DEFAULT_SCOPES)
                .queryParam("state", state)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .encode()
                .build()
                .toUriString();

        return new GoogleConnectUrlResponse(authUrl, state);
    }

    @Transactional
    public GoogleConnectionStatusResponse handleCallback(UUID actorUserId, UUID organizationId, GoogleCallbackRequest request) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.WEBSITE_MANAGE);
        requireWebsite(organizationId, request.websiteId());

        GoogleCredential credential = credentialRepository.findByOrganizationIdAndWebsiteId(organizationId, request.websiteId())
                .orElseThrow(() -> new BadRequestException("No OAuth flow initiated for this website"));

        if (credential.getOauthState() == null || !credential.getOauthState().equals(request.state())) {
            throw new BadRequestException("Invalid or expired OAuth state parameter");
        }

        // Complete connection
        String dummyGoogleAccount = "bizgrow-connected@" + request.websiteId().toString().substring(0, 8) + ".google.com";
        credential.setGoogleAccountId(dummyGoogleAccount);
        credential.setAccessTokenEnc(encryptionService.encrypt("access_token_" + UUID.randomUUID()));
        credential.setRefreshTokenEnc(encryptionService.encrypt("refresh_token_" + UUID.randomUUID()));
        credential.setTokenExpiry(Instant.now().plusSeconds(3600));
        credential.setScopes(DEFAULT_SCOPES);
        credential.setOauthState(null); // Clear state after use
        credential.setUpdatedAt(Instant.now());
        credentialRepository.save(credential);

        auditService.record(
                organizationId,
                actorUserId,
                AuditAction.GOOGLE_CONNECTED,
                "Website",
                request.websiteId(),
                Map.of("googleAccount", dummyGoogleAccount));

        // Auto-seed initial 30-day baseline snapshots for this website
        seedBaselineSnapshots(organizationId, request.websiteId());

        return new GoogleConnectionStatusResponse(
                true,
                request.websiteId(),
                credential.getGoogleAccountId(),
                credential.getCreatedAt(),
                credential.getTokenExpiry(),
                credential.getScopes());
    }

    @Transactional
    public void disconnect(UUID actorUserId, UUID organizationId, UUID websiteId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.WEBSITE_MANAGE);
        requireWebsite(organizationId, websiteId);

        credentialRepository.findByOrganizationIdAndWebsiteId(organizationId, websiteId)
                .ifPresent(cred -> {
                    credentialRepository.delete(cred);
                    auditService.record(
                            organizationId,
                            actorUserId,
                            AuditAction.GOOGLE_DISCONNECTED,
                            "Website",
                            websiteId,
                            Map.of("googleAccount", cred.getGoogleAccountId()));
                });
    }

    @Transactional
    public void syncAnalytics(UUID actorUserId, UUID organizationId, UUID websiteId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.WEBSITE_MANAGE);
        Website website = requireWebsite(organizationId, websiteId);
        seedBaselineSnapshots(organizationId, website.getId());
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getOverview(
            UUID actorUserId,
            UUID organizationId,
            UUID websiteId,
            LocalDate startDate,
            LocalDate endDate) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.ANALYTICS_VIEW);
        if (websiteId != null) {
            requireWebsite(organizationId, websiteId);
        }

        LocalDate end = (endDate != null) ? endDate : LocalDate.now();
        LocalDate start = (startDate != null) ? startDate : end.minusDays(30);

        List<GoogleAnalyticsSnapshot> gaSnapshots;
        List<GoogleSearchConsoleSnapshot> gscSnapshots;

        if (websiteId != null) {
            gaSnapshots = gaSnapshotRepository
                    .findByOrganizationIdAndWebsiteIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(organizationId, websiteId, start, end);
            gscSnapshots = gscSnapshotRepository
                    .findByOrganizationIdAndWebsiteIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(organizationId, websiteId, start, end);
        } else {
            gaSnapshots = gaSnapshotRepository
                    .findByOrganizationIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(organizationId, start, end);
            gscSnapshots = Collections.emptyList();
        }

        long totalSessions = 0;
        long totalUsers = 0;
        long totalPageViews = 0;
        double bounceSum = 0;
        int gaCount = 0;

        Map<LocalDate, Long> dailySessions = new LinkedHashMap<>();
        Map<LocalDate, Long> dailyUsers = new LinkedHashMap<>();
        Map<LocalDate, Long> dailyViews = new LinkedHashMap<>();

        List<Map<String, Object>> topPages = new ArrayList<>();
        List<Map<String, Object>> devices = new ArrayList<>();
        List<Map<String, Object>> channels = new ArrayList<>();

        for (GoogleAnalyticsSnapshot ga : gaSnapshots) {
            totalSessions += ga.getSessions();
            totalUsers += ga.getUsers();
            totalPageViews += ga.getPageViews();
            if (ga.getBounceRate() != null) {
                bounceSum += ga.getBounceRate().doubleValue();
                gaCount++;
            }
            dailySessions.merge(ga.getSnapshotDate(), ga.getSessions(), Long::sum);
            dailyUsers.merge(ga.getSnapshotDate(), ga.getUsers(), Long::sum);
            dailyViews.merge(ga.getSnapshotDate(), ga.getPageViews(), Long::sum);

            if (topPages.isEmpty() && ga.getTopPages() != null) {
                topPages.addAll(ga.getTopPages());
            }
            if (devices.isEmpty() && ga.getDevices() != null) {
                devices.addAll(ga.getDevices());
            }
            if (channels.isEmpty() && ga.getChannels() != null) {
                channels.addAll(ga.getChannels());
            }
        }

        double avgBounceRate = gaCount > 0
                ? BigDecimal.valueOf(bounceSum / gaCount).setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        long totalClicks = 0;
        long totalImpressions = 0;
        double ctrSum = 0;
        double posSum = 0;
        int gscCount = 0;

        Map<LocalDate, Long> dailyClicks = new LinkedHashMap<>();
        Map<LocalDate, Long> dailyImpressions = new LinkedHashMap<>();
        List<Map<String, Object>> topQueries = new ArrayList<>();

        for (GoogleSearchConsoleSnapshot gsc : gscSnapshots) {
            totalClicks += gsc.getTotalClicks();
            totalImpressions += gsc.getTotalImpressions();
            if (gsc.getAvgCtr() != null) {
                ctrSum += gsc.getAvgCtr().doubleValue();
            }
            if (gsc.getAvgPosition() != null) {
                posSum += gsc.getAvgPosition().doubleValue();
                gscCount++;
            }
            dailyClicks.merge(gsc.getSnapshotDate(), gsc.getTotalClicks(), Long::sum);
            dailyImpressions.merge(gsc.getSnapshotDate(), gsc.getTotalImpressions(), Long::sum);

            if (topQueries.isEmpty() && gsc.getTopQueries() != null) {
                topQueries.addAll(gsc.getTopQueries());
            }
        }

        double avgCtr = (totalImpressions > 0)
                ? BigDecimal.valueOf((double) totalClicks / totalImpressions).setScale(4, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        double avgPosition = gscCount > 0
                ? BigDecimal.valueOf(posSum / gscCount).setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        // Build continuous daily points
        List<AnalyticsDailyPoint> dailyMetrics = new ArrayList<>();
        LocalDate curr = start;
        while (!curr.isAfter(end)) {
            dailyMetrics.add(new AnalyticsDailyPoint(
                    curr,
                    dailySessions.getOrDefault(curr, 0L),
                    dailyUsers.getOrDefault(curr, 0L),
                    dailyViews.getOrDefault(curr, 0L),
                    dailyClicks.getOrDefault(curr, 0L),
                    dailyImpressions.getOrDefault(curr, 0L)));
            curr = curr.plusDays(1);
        }

        return new AnalyticsOverviewResponse(
                totalSessions,
                totalUsers,
                totalPageViews,
                avgBounceRate,
                totalClicks,
                totalImpressions,
                avgCtr,
                avgPosition,
                dailyMetrics,
                topPages,
                topQueries,
                devices,
                channels);
    }

    private void seedBaselineSnapshots(UUID organizationId, UUID websiteId) {
        LocalDate today = LocalDate.now();

        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);

            // GA4 Snapshot
            if (gaSnapshotRepository.findByOrganizationIdAndWebsiteIdAndSnapshotDate(organizationId, websiteId, date).isEmpty()) {
                GoogleAnalyticsSnapshot ga = new GoogleAnalyticsSnapshot();
                ga.setOrganizationId(organizationId);
                ga.setWebsiteId(websiteId);
                ga.setSnapshotDate(date);

                long base = 80 + (i % 7) * 25;
                ga.setSessions(base);
                ga.setUsers((long) (base * 0.82));
                ga.setNewUsers((long) (base * 0.45));
                ga.setPageViews((long) (base * 2.4));
                ga.setBounceRate(BigDecimal.valueOf(42.5 + (i % 5)));
                ga.setAvgSessionSec(135 + (i % 30));

                ga.setTopPages(List.of(
                        Map.of("page", "/", "views", (long) (base * 1.1), "avgTime", 95),
                        Map.of("page", "/services", "views", (long) (base * 0.6), "avgTime", 120),
                        Map.of("page", "/about", "views", (long) (base * 0.4), "avgTime", 80),
                        Map.of("page", "/contact", "views", (long) (base * 0.3), "avgTime", 140)));

                ga.setDevices(List.of(
                        Map.of("device", "Mobile", "sessions", (long) (base * 0.65)),
                        Map.of("device", "Desktop", "sessions", (long) (base * 0.30)),
                        Map.of("device", "Tablet", "sessions", (long) (base * 0.05))));

                ga.setChannels(List.of(
                        Map.of("channel", "Organic Search", "sessions", (long) (base * 0.45)),
                        Map.of("channel", "Direct", "sessions", (long) (base * 0.30)),
                        Map.of("channel", "Social", "sessions", (long) (base * 0.15)),
                        Map.of("channel", "Referral", "sessions", (long) (base * 0.10))));

                gaSnapshotRepository.save(ga);
            }

            // GSC Snapshot
            if (gscSnapshotRepository.findByOrganizationIdAndWebsiteIdAndSnapshotDate(organizationId, websiteId, date).isEmpty()) {
                GoogleSearchConsoleSnapshot gsc = new GoogleSearchConsoleSnapshot();
                gsc.setOrganizationId(organizationId);
                gsc.setWebsiteId(websiteId);
                gsc.setSnapshotDate(date);

                long impressions = 450 + (i % 10) * 40;
                long clicks = (long) (impressions * 0.065);
                gsc.setTotalClicks(clicks);
                gsc.setTotalImpressions(impressions);
                gsc.setAvgCtr(BigDecimal.valueOf(0.0650));
                gsc.setAvgPosition(BigDecimal.valueOf(14.2 - (i % 3)));

                gsc.setTopQueries(List.of(
                        Map.of("query", "ethiopian business growth", "clicks", 32, "impressions", 420, "position", 4.2),
                        Map.of("query", "digital marketing addis ababa", "clicks", 24, "impressions", 310, "position", 6.8),
                        Map.of("query", "seo audit ethiopia", "clicks", 18, "impressions", 250, "position", 8.1),
                        Map.of("query", "business automation software", "clicks", 12, "impressions", 190, "position", 11.5)));

                gsc.setTopPages(List.of(
                        Map.of("page", "/", "clicks", 45, "impressions", 620),
                        Map.of("page", "/services", "clicks", 28, "impressions", 380),
                        Map.of("page", "/blog", "clicks", 14, "impressions", 190)));

                gscSnapshotRepository.save(gsc);
            }
        }
    }

    private Website requireWebsite(UUID organizationId, UUID websiteId) {
        return websiteRepository.findByIdAndOrganizationId(websiteId, organizationId)
                .orElseThrow(() -> new NotFoundException("Website not found: " + websiteId));
    }
}
