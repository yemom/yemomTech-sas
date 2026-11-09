package com.YemomBizGrow.integration.google;

import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.integration.google.dto.AnalyticsDailyPoint;
import com.YemomBizGrow.integration.google.dto.AnalyticsOverviewResponse;
import com.YemomBizGrow.integration.google.dto.GoogleCallbackRequest;
import com.YemomBizGrow.integration.google.dto.GoogleConnectUrlResponse;
import com.YemomBizGrow.integration.google.dto.GoogleAccessibleResourcesResponse;
import com.YemomBizGrow.integration.google.dto.GoogleConnectionStatusResponse;
import com.YemomBizGrow.integration.google.dto.GooglePropertySelectionRequest;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final GoogleOAuthClient oAuthClient;

    public GoogleIntegrationService(
            GoogleCredentialRepository credentialRepository,
            GoogleAnalyticsSnapshotRepository gaSnapshotRepository,
            GoogleSearchConsoleSnapshotRepository gscSnapshotRepository,
            WebsiteRepository websiteRepository,
            AccessControlService accessControlService,
            AuditService auditService,
            EncryptionService encryptionService,
            GoogleIntegrationProperties googleProps,
            GoogleOAuthClient oAuthClient) {
        this.credentialRepository = credentialRepository;
        this.gaSnapshotRepository = gaSnapshotRepository;
        this.gscSnapshotRepository = gscSnapshotRepository;
        this.websiteRepository = websiteRepository;
        this.accessControlService = accessControlService;
        this.auditService = auditService;
        this.encryptionService = encryptionService;
        this.googleProps = googleProps;
        this.oAuthClient = oAuthClient;
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
                        cred.getScopes(),
                        cred.getGa4PropertyId(),
                        cred.getGscSiteUrl(),
                        cred.getLastGa4SyncAt(),
                        cred.getLastGscSyncAt()))
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

        if (googleProps.getClientId().isBlank() || googleProps.getClientSecret().isBlank()) {
            throw new BadRequestException("Google integration credentials (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET) are missing");
        }

        credential.setOauthState(state);
        credential.setOauthStateExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        credential.setInitiatingUserId(actorUserId);
        credential.setUpdatedAt(Instant.now());
        credentialRepository.save(credential);

        String clientId = googleProps.getClientId();
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

        if (googleProps.getClientId().isBlank() || googleProps.getClientSecret().isBlank()) {
            throw new BadRequestException("Google integration credentials (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET) are missing");
        }

        GoogleCredential credential = credentialRepository.findByOrganizationIdAndWebsiteId(organizationId, request.websiteId())
                .orElseThrow(() -> new BadRequestException("No OAuth flow initiated for this website"));

        if (credential.getInitiatingUserId() != null && !credential.getInitiatingUserId().equals(actorUserId)) {
            throw new BadRequestException("OAuth callback must be completed by the user who initiated the connection");
        }

        // Atomic single-use state consumption to prevent replay or concurrent duplicate callbacks
        int consumed = credentialRepository.consumeOauthState(organizationId, request.websiteId(), request.state(), Instant.now());
        if (consumed == 0) {
            throw new BadRequestException("Invalid, expired, or replayed OAuth state parameter");
        }

        GoogleOAuthClient.TokenResponse tokens;
        GoogleOAuthClient.UserInfoResponse userInfo;
        try {
            tokens = oAuthClient.exchangeAuthorizationCode(
                    request.code(),
                    googleProps.getClientId(),
                    googleProps.getClientSecret(),
                    googleProps.getRedirectUri()
            );
            userInfo = oAuthClient.fetchUserInfo(tokens.accessToken());
        } catch (Exception e) {
            log.error("Failed to complete Google OAuth exchange for website {}", request.websiteId(), e);
            throw new BadRequestException("Failed to authenticate with Google: " + e.getMessage());
        }

        String googleAccount = (userInfo.email() != null && !userInfo.email().isBlank())
                ? userInfo.email()
                : (userInfo.id() != null ? userInfo.id() : "connected-user");

        credential.setGoogleAccountId(googleAccount);
        credential.setAccessTokenEnc(encryptionService.encrypt(tokens.accessToken()));
        if (tokens.refreshToken() != null && !tokens.refreshToken().isBlank()) {
            credential.setRefreshTokenEnc(encryptionService.encrypt(tokens.refreshToken()));
        }
        credential.setTokenExpiry(Instant.now().plusSeconds(tokens.expiresIn()));
        credential.setScopes(tokens.scope() != null && !tokens.scope().isBlank() ? tokens.scope() : DEFAULT_SCOPES);
        credential.setOauthState(null); // Clear state after use
        credential.setOauthStateExpiresAt(null);
        credential.setUpdatedAt(Instant.now());
        credentialRepository.save(credential);

        auditService.record(
                organizationId,
                actorUserId,
                AuditAction.GOOGLE_CONNECTED,
                "Website",
                request.websiteId(),
                Map.of("googleAccount", googleAccount));

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

        GoogleCredential cred = credentialRepository.findByOrganizationIdAndWebsiteId(organizationId, websiteId)
                .orElseThrow(() -> new BadRequestException("Google account is not connected for website: " + websiteId));

        String accessToken = getValidAccessToken(cred);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        try {
            // Real GA4 query using tenant's stored property ID, falling back to global config
            String propertyId = (cred.getGa4PropertyId() != null && !cred.getGa4PropertyId().isBlank())
                    ? cred.getGa4PropertyId()
                    : googleProps.getGa4PropertyId();
            if (propertyId != null && !propertyId.isBlank()) {
                List<GoogleOAuthClient.Ga4DailyMetric> gaMetrics = oAuthClient.fetchGa4DailyMetrics(
                        accessToken, propertyId, startDate, endDate
                );
                for (GoogleOAuthClient.Ga4DailyMetric metric : gaMetrics) {
                    GoogleAnalyticsSnapshot snapshot = gaSnapshotRepository
                            .findByOrganizationIdAndWebsiteIdAndSnapshotDate(organizationId, websiteId, metric.date())
                            .orElseGet(() -> {
                                GoogleAnalyticsSnapshot s = new GoogleAnalyticsSnapshot();
                                s.setOrganizationId(organizationId);
                                s.setWebsiteId(websiteId);
                                s.setSnapshotDate(metric.date());
                                return s;
                            });
                    snapshot.setSessions(metric.sessions());
                    snapshot.setUsers(metric.users());
                    snapshot.setPageViews(metric.pageViews());
                    snapshot.setBounceRate(BigDecimal.valueOf(metric.bounceRate()));
                    gaSnapshotRepository.save(snapshot);
                }
            }

            // Real Search Console query using tenant's stored site URL or website domain
            String siteUrl = (cred.getGscSiteUrl() != null && !cred.getGscSiteUrl().isBlank())
                    ? cred.getGscSiteUrl()
                    : (website.getDomain().startsWith("http") ? website.getDomain() : "https://" + website.getDomain());
            List<GoogleOAuthClient.GscDailyMetric> gscMetrics = oAuthClient.fetchGscDailyMetrics(
                    accessToken, siteUrl, startDate, endDate
            );
            for (GoogleOAuthClient.GscDailyMetric metric : gscMetrics) {
                GoogleSearchConsoleSnapshot snapshot = gscSnapshotRepository
                        .findByOrganizationIdAndWebsiteIdAndSnapshotDate(organizationId, websiteId, metric.date())
                        .orElseGet(() -> {
                            GoogleSearchConsoleSnapshot s = new GoogleSearchConsoleSnapshot();
                            s.setOrganizationId(organizationId);
                            s.setWebsiteId(websiteId);
                            s.setSnapshotDate(metric.date());
                            return s;
                        });
                snapshot.setTotalClicks(metric.clicks());
                snapshot.setTotalImpressions(metric.impressions());
                snapshot.setAvgCtr(BigDecimal.valueOf(metric.ctr()));
                snapshot.setAvgPosition(BigDecimal.valueOf(metric.position()));
                gscSnapshotRepository.save(snapshot);
            }
        } catch (Exception e) {
            log.error("Failed to sync Google analytics data for website {}", websiteId, e);
            throw new BadRequestException("Google analytics sync failed: " + e.getMessage());
        }
    }

    private String getValidAccessToken(GoogleCredential cred) {
        String accessToken = encryptionService.decrypt(cred.getAccessTokenEnc());
        if (cred.getTokenExpiry() != null && Instant.now().isAfter(cred.getTokenExpiry().minusSeconds(120))) {
            if (cred.getRefreshTokenEnc() != null) {
                try {
                    String refreshToken = encryptionService.decrypt(cred.getRefreshTokenEnc());
                    GoogleOAuthClient.TokenResponse refreshed = oAuthClient.refreshAccessToken(
                            refreshToken,
                            googleProps.getClientId(),
                            googleProps.getClientSecret()
                    );
                    accessToken = refreshed.accessToken();
                    cred.setAccessTokenEnc(encryptionService.encrypt(accessToken));
                    cred.setTokenExpiry(Instant.now().plusSeconds(refreshed.expiresIn()));
                    credentialRepository.save(cred);
                } catch (Exception e) {
                    log.error("Failed to refresh Google token for account {}", cred.getGoogleAccountId(), e);
                }
            }
        }
        return accessToken;
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

        // Build continuous daily points (real data or zeroes if no data recorded)
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

    private Website requireWebsite(UUID organizationId, UUID websiteId) {
        return websiteRepository.findByIdAndOrganizationId(websiteId, organizationId)
                .orElseThrow(() -> new NotFoundException("Website not found: " + websiteId));
    }

    @Transactional(readOnly = true)
    public GoogleAccessibleResourcesResponse listAccessibleResources(UUID actorUserId, UUID organizationId, UUID websiteId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.ORG_VIEW);
        requireWebsite(organizationId, websiteId);

        GoogleCredential cred = credentialRepository.findByOrganizationIdAndWebsiteId(organizationId, websiteId)
                .orElseThrow(() -> new BadRequestException("Google account is not connected for website: " + websiteId));

        String accessToken = getValidAccessToken(cred);

        List<GoogleOAuthClient.Ga4PropertyItem> gaProps = oAuthClient.listGa4Properties(accessToken);
        List<GoogleOAuthClient.GscSiteItem> gscSites = oAuthClient.listSearchConsoleSites(accessToken);

        List<GoogleAccessibleResourcesResponse.Ga4PropertyOption> propOptions = gaProps.stream()
                .map(p -> new GoogleAccessibleResourcesResponse.Ga4PropertyOption(p.propertyId(), p.displayName()))
                .toList();

        List<GoogleAccessibleResourcesResponse.GscSiteOption> siteOptions = gscSites.stream()
                .map(s -> new GoogleAccessibleResourcesResponse.GscSiteOption(s.siteUrl(), s.permissionLevel()))
                .toList();

        return new GoogleAccessibleResourcesResponse(
                propOptions,
                siteOptions,
                cred.getGa4PropertyId(),
                cred.getGscSiteUrl()
        );
    }

    @Transactional
    public GoogleConnectionStatusResponse selectProperties(
            UUID actorUserId,
            UUID organizationId,
            UUID websiteId,
            GooglePropertySelectionRequest request
    ) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.WEBSITE_MANAGE);
        requireWebsite(organizationId, websiteId);

        GoogleCredential cred = credentialRepository.findByOrganizationIdAndWebsiteId(organizationId, websiteId)
                .orElseThrow(() -> new BadRequestException("Google account is not connected for website: " + websiteId));

        if (request.ga4PropertyId() != null) {
            cred.setGa4PropertyId(request.ga4PropertyId().trim());
        }
        if (request.gscSiteUrl() != null) {
            cred.setGscSiteUrl(request.gscSiteUrl().trim());
        }
        cred.setUpdatedAt(Instant.now());
        GoogleCredential saved = credentialRepository.save(cred);

        return new GoogleConnectionStatusResponse(
                true,
                saved.getWebsiteId(),
                saved.getGoogleAccountId(),
                saved.getCreatedAt(),
                saved.getTokenExpiry(),
                saved.getScopes(),
                saved.getGa4PropertyId(),
                saved.getGscSiteUrl(),
                saved.getLastGa4SyncAt(),
                saved.getLastGscSyncAt()
        );
    }
}
