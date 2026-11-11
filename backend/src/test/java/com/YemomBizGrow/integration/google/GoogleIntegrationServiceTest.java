package com.YemomBizGrow.integration.google;

import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.integration.google.dto.AnalyticsOverviewResponse;
import com.YemomBizGrow.integration.google.dto.GoogleCallbackRequest;
import com.YemomBizGrow.integration.google.dto.GoogleConnectUrlResponse;
import com.YemomBizGrow.integration.google.dto.GoogleConnectionStatusResponse;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import com.YemomBizGrow.website.Website;
import com.YemomBizGrow.website.WebsiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleIntegrationServiceTest {

    @Mock
    private GoogleCredentialRepository credentialRepository;
    @Mock
    private GoogleAnalyticsSnapshotRepository gaSnapshotRepository;
    @Mock
    private GoogleSearchConsoleSnapshotRepository gscSnapshotRepository;
    @Mock
    private WebsiteRepository websiteRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private AuditService auditService;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private GoogleOAuthClient oAuthClient;

    private GoogleIntegrationProperties googleProps;
    private GoogleIntegrationService service;

    private final UUID actorId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID websiteId = UUID.randomUUID();
    private Website website;

    @BeforeEach
    void setUp() {
        googleProps = new GoogleIntegrationProperties();
        googleProps.setClientId("test-client-id");
        googleProps.setClientSecret("test-secret");
        googleProps.setRedirectUri("http://localhost:3000/callback");

        service = new GoogleIntegrationService(
                credentialRepository,
                gaSnapshotRepository,
                gscSnapshotRepository,
                websiteRepository,
                accessControlService,
                auditService,
                encryptionService,
                googleProps,
                oAuthClient);

        website = new Website();
        website.setId(websiteId);
        website.setOrganizationId(orgId);
        website.setName("Test Website");
        website.setDomain("example.com");
    }

    @Test
    void getConnectionStatus_whenNotConnected_returnsFalse() {
        when(websiteRepository.findByIdAndOrganizationId(websiteId, orgId)).thenReturn(Optional.of(website));
        when(credentialRepository.findByOrganizationIdAndWebsiteId(orgId, websiteId)).thenReturn(Optional.empty());

        GoogleConnectionStatusResponse status = service.getConnectionStatus(actorId, orgId, websiteId);

        assertThat(status.connected()).isFalse();
        assertThat(status.websiteId()).isEqualTo(websiteId);
        verify(accessControlService).requirePermission(actorId, orgId, Permission.ANALYTICS_VIEW);
    }

    @Test
    void getConnectionStatus_whenConnected_returnsTrue() {
        when(websiteRepository.findByIdAndOrganizationId(websiteId, orgId)).thenReturn(Optional.of(website));

        GoogleCredential cred = new GoogleCredential();
        cred.setOrganizationId(orgId);
        cred.setWebsiteId(websiteId);
        cred.setGoogleAccountId("account@example.com");
        cred.setCreatedAt(Instant.now());
        cred.setTokenExpiry(Instant.now().plusSeconds(3600));
        cred.setScopes("openid email");
        when(credentialRepository.findByOrganizationIdAndWebsiteId(orgId, websiteId)).thenReturn(Optional.of(cred));

        GoogleConnectionStatusResponse status = service.getConnectionStatus(actorId, orgId, websiteId);

        assertThat(status.connected()).isTrue();
        assertThat(status.googleAccountId()).isEqualTo("account@example.com");
    }

    @Test
    void generateConnectUrl_savesStateAndReturnsAuthUrl() {
        when(websiteRepository.findByIdAndOrganizationId(websiteId, orgId)).thenReturn(Optional.of(website));
        when(credentialRepository.findByOrganizationIdAndWebsiteId(orgId, websiteId)).thenReturn(Optional.empty());
        when(encryptionService.encrypt(any())).thenReturn("encrypted_dummy");
        when(oAuthClient.buildAuthorizationUrl(eq("test-client-id"), eq("http://localhost:3000/callback")))
                .thenReturn(new String[]{"https://accounts.google.com/o/oauth2/v2/auth?client_id=test-client-id&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fcallback&state=test-state", "test-code-verifier"});

        GoogleConnectUrlResponse response = service.generateConnectUrl(actorId, orgId, websiteId);

        assertThat(response.authUrl()).contains("accounts.google.com");
        assertThat(response.authUrl()).contains("client_id=test-client-id");
        assertThat(response.state()).isNotBlank();

        verify(accessControlService).requirePermission(actorId, orgId, Permission.WEBSITE_MANAGE);
        verify(credentialRepository).save(any(GoogleCredential.class));
    }

    @Test
    void handleCallback_withValidState_completesConnection() throws Exception {
        when(websiteRepository.findByIdAndOrganizationId(websiteId, orgId)).thenReturn(Optional.of(website));

        GoogleCredential cred = new GoogleCredential();
        cred.setOrganizationId(orgId);
        cred.setWebsiteId(websiteId);
        cred.setOauthState("valid-state-123");
        cred.setCodeVerifier("test-code-verifier");
        when(credentialRepository.findByOrganizationIdAndWebsiteId(orgId, websiteId)).thenReturn(Optional.of(cred));
        when(encryptionService.encrypt(any())).thenReturn("encrypted_token");

        when(oAuthClient.exchangeAuthorizationCode(eq("test-auth-code"), eq("test-client-id"), eq("test-secret"), eq("http://localhost:3000/callback"), eq("test-code-verifier")))
                .thenReturn(new GoogleOAuthClient.TokenResponse("real_access_token", "real_refresh_token", 3600L, "openid email"));
        when(oAuthClient.fetchUserInfo("real_access_token"))
                .thenReturn(new GoogleOAuthClient.UserInfoResponse("123", "verified@example.com", "Verified User"));

        when(credentialRepository.consumeOauthState(eq(orgId), eq(websiteId), eq("valid-state-123"), any())).thenReturn(1);
        GoogleCallbackRequest req = new GoogleCallbackRequest(websiteId, "test-auth-code", "valid-state-123");
        GoogleConnectionStatusResponse resp = service.handleCallback(actorId, orgId, req);

        assertThat(resp.connected()).isTrue();
        assertThat(resp.googleAccountId()).isEqualTo("verified@example.com");
        assertThat(cred.getOauthState()).isNull(); // state cleared
        verify(auditService).record(eq(orgId), eq(actorId), eq(AuditAction.GOOGLE_CONNECTED), eq("Website"), eq(websiteId), any());
    }

    @Test
    void handleCallback_withInvalidState_throwsBadRequest() {
        when(websiteRepository.findByIdAndOrganizationId(websiteId, orgId)).thenReturn(Optional.of(website));

        GoogleCredential cred = new GoogleCredential();
        cred.setOrganizationId(orgId);
        cred.setWebsiteId(websiteId);
        cred.setOauthState("original-state");
        when(credentialRepository.findByOrganizationIdAndWebsiteId(orgId, websiteId)).thenReturn(Optional.of(cred));

        GoogleCallbackRequest req = new GoogleCallbackRequest(websiteId, "code", "tampered-state");
        assertThatThrownBy(() -> service.handleCallback(actorId, orgId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("state");
    }

    @Test
    void disconnect_removesCredentialAndLogsAudit() {
        when(websiteRepository.findByIdAndOrganizationId(websiteId, orgId)).thenReturn(Optional.of(website));

        GoogleCredential cred = new GoogleCredential();
        cred.setOrganizationId(orgId);
        cred.setWebsiteId(websiteId);
        cred.setGoogleAccountId("user@example.com");
        when(credentialRepository.findByOrganizationIdAndWebsiteId(orgId, websiteId)).thenReturn(Optional.of(cred));

        service.disconnect(actorId, orgId, websiteId);

        verify(credentialRepository).delete(cred);
        verify(auditService).record(eq(orgId), eq(actorId), eq(AuditAction.GOOGLE_DISCONNECTED), eq("Website"), eq(websiteId), any());
    }

    @Test
    void getOverview_aggregatesDataCorrectly() {
        when(websiteRepository.findByIdAndOrganizationId(websiteId, orgId)).thenReturn(Optional.of(website));

        LocalDate date1 = LocalDate.now().minusDays(1);
        GoogleAnalyticsSnapshot ga = new GoogleAnalyticsSnapshot();
        ga.setOrganizationId(orgId);
        ga.setWebsiteId(websiteId);
        ga.setSnapshotDate(date1);
        ga.setSessions(100);
        ga.setUsers(80);
        ga.setPageViews(250);
        ga.setBounceRate(BigDecimal.valueOf(40.0));

        GoogleSearchConsoleSnapshot gsc = new GoogleSearchConsoleSnapshot();
        gsc.setOrganizationId(orgId);
        gsc.setWebsiteId(websiteId);
        gsc.setSnapshotDate(date1);
        gsc.setTotalClicks(20);
        gsc.setTotalImpressions(400);
        gsc.setAvgCtr(BigDecimal.valueOf(0.05));
        gsc.setAvgPosition(BigDecimal.valueOf(12.5));

        when(gaSnapshotRepository.findByOrganizationIdAndWebsiteIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                eq(orgId), eq(websiteId), any(), any())).thenReturn(List.of(ga));
        when(gscSnapshotRepository.findByOrganizationIdAndWebsiteIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                eq(orgId), eq(websiteId), any(), any())).thenReturn(List.of(gsc));

        AnalyticsOverviewResponse overview = service.getOverview(actorId, orgId, websiteId, null, null);

        assertThat(overview.totalSessions()).isEqualTo(100);
        assertThat(overview.totalUsers()).isEqualTo(80);
        assertThat(overview.totalPageViews()).isEqualTo(250);
        assertThat(overview.avgBounceRate()).isEqualTo(40.0);
        assertThat(overview.totalClicks()).isEqualTo(20);
        assertThat(overview.totalImpressions()).isEqualTo(400);
        assertThat(overview.dailyMetrics()).isNotEmpty();
    }

    @Test
    void handleCallback_whenCredentialsMissing_throwsBadRequest() {
        googleProps.setClientId("");
        googleProps.setClientSecret("");

        when(websiteRepository.findByIdAndOrganizationId(websiteId, orgId)).thenReturn(Optional.of(website));

        GoogleCallbackRequest req = new GoogleCallbackRequest(websiteId, "code", "valid-state");
        assertThatThrownBy(() -> service.handleCallback(actorId, orgId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void getOverview_whenNoSnapshots_returnsZeroesWithoutSyntheticData() {
        when(websiteRepository.findByIdAndOrganizationId(websiteId, orgId)).thenReturn(Optional.of(website));
        when(gaSnapshotRepository.findByOrganizationIdAndWebsiteIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                eq(orgId), eq(websiteId), any(), any())).thenReturn(List.of());
        when(gscSnapshotRepository.findByOrganizationIdAndWebsiteIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                eq(orgId), eq(websiteId), any(), any())).thenReturn(List.of());

        AnalyticsOverviewResponse overview = service.getOverview(actorId, orgId, websiteId, null, null);

        assertThat(overview.totalSessions()).isEqualTo(0);
        assertThat(overview.totalUsers()).isEqualTo(0);
        assertThat(overview.totalPageViews()).isEqualTo(0);
        assertThat(overview.avgBounceRate()).isEqualTo(0.0);
        assertThat(overview.totalClicks()).isEqualTo(0);
        assertThat(overview.totalImpressions()).isEqualTo(0);
        assertThat(overview.topPages()).isEmpty();
        assertThat(overview.topQueries()).isEmpty();
    }

    @Test
    void handleCallback_whenDifferentUser_throwsBadRequest() {
        when(websiteRepository.findByIdAndOrganizationId(websiteId, orgId)).thenReturn(Optional.of(website));

        GoogleCredential cred = new GoogleCredential();
        cred.setOrganizationId(orgId);
        cred.setWebsiteId(websiteId);
        cred.setInitiatingUserId(UUID.randomUUID()); // different initiating user
        when(credentialRepository.findByOrganizationIdAndWebsiteId(orgId, websiteId)).thenReturn(Optional.of(cred));

        GoogleCallbackRequest req = new GoogleCallbackRequest(websiteId, "code", "any-state");
        assertThatThrownBy(() -> service.handleCallback(actorId, orgId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("initiated");
    }

    @Test
    void handleCallback_whenStateReplayedOrConcurrent_throwsBadRequest() {
        when(websiteRepository.findByIdAndOrganizationId(websiteId, orgId)).thenReturn(Optional.of(website));

        GoogleCredential cred = new GoogleCredential();
        cred.setOrganizationId(orgId);
        cred.setWebsiteId(websiteId);
        cred.setInitiatingUserId(actorId);
        when(credentialRepository.findByOrganizationIdAndWebsiteId(orgId, websiteId)).thenReturn(Optional.of(cred));
        // Atomic consume returns 0 because state was already consumed
        when(credentialRepository.consumeOauthState(eq(orgId), eq(websiteId), eq("replayed-state"), any())).thenReturn(0);

        GoogleCallbackRequest req = new GoogleCallbackRequest(websiteId, "code", "replayed-state");
        assertThatThrownBy(() -> service.handleCallback(actorId, orgId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("replayed");
    }
}
