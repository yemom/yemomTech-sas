package com.YemomBizGrow.ai;

import com.YemomBizGrow.ai.dto.AiRecommendationResponse;
import com.YemomBizGrow.ai.dto.ChatbotConfigRequest;
import com.YemomBizGrow.ai.dto.ChatbotConfigResponse;
import com.YemomBizGrow.ai.dto.PublicChatMessageRequest;
import com.YemomBizGrow.ai.dto.PublicChatMessageResponse;
import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.common.exception.TooManyRequestsException;
import com.YemomBizGrow.common.response.PageResponse;
import com.YemomBizGrow.crm.Lead;
import com.YemomBizGrow.crm.LeadRepository;
import com.YemomBizGrow.integration.google.GoogleAnalyticsSnapshotRepository;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.organization.OrganizationRepository;
import com.YemomBizGrow.seo.SeoAuditRepository;
import com.YemomBizGrow.website.WebsiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiRecommendationRepository recommendationRepository;
    @Mock
    private ChatbotConfigRepository chatbotConfigRepository;
    @Mock
    private ChatbotConversationRepository conversationRepository;
    @Mock
    private ChatbotMessageRepository messageRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private WebsiteRepository websiteRepository;
    @Mock
    private SeoAuditRepository seoAuditRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private GoogleAnalyticsSnapshotRepository gaSnapshotRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private AiProvider aiProvider;
    @Mock
    private AiGenerationGuard generationGuard;
    @Mock
    private AuditService auditService;

    private AiService aiService;

    private final UUID actorId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private Organization organization;

    @BeforeEach
    void setUp() {
        aiService = new AiService(
                recommendationRepository,
                chatbotConfigRepository,
                conversationRepository,
                messageRepository,
                organizationRepository,
                websiteRepository,
                seoAuditRepository,
                leadRepository,
                gaSnapshotRepository,
                accessControlService,
                aiProvider,
                generationGuard,
                auditService);

        organization = new Organization();
        organization.setId(orgId);
        organization.setName("Bole Trading");
        organization.setSlug("bole-trading");
    }

    @Test
    void getRecommendations_whenExistingFound_returnsPage() {
        AiRecommendation rec = new AiRecommendation();
        rec.setId(UUID.randomUUID());
        rec.setOrganizationId(orgId);
        rec.setCategory(RecommendationCategory.SEO);
        rec.setPriority(RecommendationPriority.HIGH);
        rec.setTitle("Fix Meta Tags");
        rec.setProblem("Missing titles");
        rec.setActionRecommendation("Add tags");
        rec.setStatus(RecommendationStatus.OPEN);

        when(recommendationRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                eq(orgId), eq(RecommendationStatus.OPEN), any())).thenReturn(new PageImpl<>(List.of(rec)));

        PageResponse<AiRecommendationResponse> resp = aiService.getRecommendations(
                actorId, orgId, RecommendationStatus.OPEN, PageRequest.of(0, 20));

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.content().get(0).title()).isEqualTo("Fix Meta Tags");
        verify(accessControlService).requirePermission(actorId, orgId, Permission.ORG_VIEW);
    }

    @Test
    void getRecommendations_isAPureRead_neverGeneratesAndNeverWrites() {
        // A GET must not create recommendations, must not call the provider and
        // must not write anything: generation is an explicit POST action.
        when(recommendationRepository.findByOrganizationIdOrderByCreatedAtDesc(eq(orgId), any()))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<AiRecommendationResponse> resp = aiService.getRecommendations(
                actorId, orgId, null, PageRequest.of(0, 20));

        assertThat(resp.content()).isEmpty();
        verify(aiProvider, never()).generateRecommendations(any(), any(), any(), anyLong(), anyLong(), any());
        verify(recommendationRepository, never()).saveAll(any());
        verify(recommendationRepository, never()).save(any(AiRecommendation.class));
    }

    @Test
    void getRecommendations_doesNotGenerateWhenEmpty() {
        // Regression guard for the defect: an empty list used to trigger
        // generateRecommendationsInternal() from inside the GET.
        when(recommendationRepository.findByOrganizationIdOrderByCreatedAtDesc(eq(orgId), any()))
                .thenReturn(new PageImpl<>(List.of()));

        aiService.getRecommendations(actorId, orgId, null, PageRequest.of(0, 20));

        verify(organizationRepository, never()).findById(any());
        verify(leadRepository, never()).countByOrganizationIdAndStatus(any(), any());
    }

    @Test
    void generateRecommendations_createsAndAudits() {
        AiRecommendation rec = new AiRecommendation();
        rec.setId(UUID.randomUUID());
        rec.setOrganizationId(orgId);
        rec.setCategory(RecommendationCategory.SEO);
        rec.setPriority(RecommendationPriority.MEDIUM);
        rec.setTitle("Improve load time");
        rec.setStatus(RecommendationStatus.OPEN);

        when(generationGuard.isRateLimited(orgId)).thenReturn(false);
        when(generationGuard.tryAcquire(orgId)).thenReturn(true);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(websiteRepository.findTop5ByOrganizationIdOrderByCreatedAtDesc(orgId)).thenReturn(List.of());
        when(leadRepository.countByOrganizationIdAndStatus(eq(orgId), any())).thenReturn(0L);
        when(leadRepository.countByOrganizationIdAndStage(eq(orgId), any())).thenReturn(0L);
        when(aiProvider.generateRecommendations(any(), any(), any(), anyLong(), anyLong(), any()))
                .thenReturn(List.of(rec));
        when(recommendationRepository.saveAll(any())).thenReturn(List.of(rec));

        List<AiRecommendationResponse> created =
                aiService.generateRecommendations(actorId, orgId);

        assertThat(created).hasSize(1);
        verify(aiProvider).generateRecommendations(any(), any(), any(), anyLong(), anyLong(), any());
        verify(auditService).record(eq(actorId), eq(orgId), eq(AuditAction.AI_RECOMMENDATIONS_GENERATED),
                eq("ai_recommendations"), eq(orgId), any());
        verify(generationGuard).release(orgId);
    }

    @Test
    void generateRecommendations_isRateLimited() {
        when(generationGuard.isRateLimited(orgId)).thenReturn(true);

        assertThatThrownBy(() -> aiService.generateRecommendations(actorId, orgId))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("limited to");

        verify(aiProvider, never()).generateRecommendations(any(), any(), any(), anyLong(), anyLong(), any());
        verify(generationGuard, never()).tryAcquire(any());
    }

    @Test
    void generateRecommendations_isRejectedWhenOneIsAlreadyRunning() {
        when(generationGuard.isRateLimited(orgId)).thenReturn(false);
        when(generationGuard.tryAcquire(orgId)).thenReturn(false);

        assertThatThrownBy(() -> aiService.generateRecommendations(actorId, orgId))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("already running");

        verify(aiProvider, never()).generateRecommendations(any(), any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void generateRecommendations_recordsFailureAndReleasesTheLock() {
        when(generationGuard.isRateLimited(orgId)).thenReturn(false);
        when(generationGuard.tryAcquire(orgId)).thenReturn(true);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(websiteRepository.findTop5ByOrganizationIdOrderByCreatedAtDesc(orgId)).thenReturn(List.of());
        when(aiProvider.generateRecommendations(any(), any(), any(), anyLong(), anyLong(), any()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertThatThrownBy(() -> aiService.generateRecommendations(actorId, orgId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider unavailable");

        verify(auditService).record(eq(actorId), eq(orgId), eq(AuditAction.AI_GENERATION_FAILED),
                eq("ai_recommendations"), eq(orgId), any());
        verify(generationGuard).release(orgId);
    }

    @Test
    void updateStatus_changesStatusSuccessfully() {
        UUID recId = UUID.randomUUID();
        AiRecommendation rec = new AiRecommendation();
        rec.setId(recId);
        rec.setOrganizationId(orgId);
        rec.setStatus(RecommendationStatus.OPEN);

        when(recommendationRepository.findByIdAndOrganizationId(recId, orgId)).thenReturn(Optional.of(rec));
        when(recommendationRepository.save(any(AiRecommendation.class))).thenAnswer(i -> i.getArgument(0));

        AiRecommendationResponse res = aiService.updateStatus(actorId, orgId, recId, RecommendationStatus.APPLIED);

        assertThat(res.status()).isEqualTo(RecommendationStatus.APPLIED);
        verify(accessControlService).requirePermission(actorId, orgId, Permission.ORG_UPDATE);
    }

    @Test
    void updateChatbotConfig_updatesFieldsAndReturns() {
        ChatbotConfig config = new ChatbotConfig();
        config.setOrganizationId(orgId);

        when(chatbotConfigRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(config));
        when(chatbotConfigRepository.save(any(ChatbotConfig.class))).thenAnswer(i -> i.getArgument(0));

        ChatbotConfigRequest req = new ChatbotConfigRequest(
                "Addis Bot",
                "Selam!",
                "Help politely",
                "#0d9488",
                Collections.emptyList(),
                true);

        ChatbotConfigResponse resp = aiService.updateChatbotConfig(actorId, orgId, req);

        assertThat(resp.botName()).isEqualTo("Addis Bot");
        assertThat(resp.greetingMessage()).isEqualTo("Selam!");
        assertThat(resp.primaryColor()).isEqualTo("#0d9488");
        verify(accessControlService).requirePermission(actorId, orgId, Permission.ORG_UPDATE);
    }

    @Test
    void handlePublicChat_whenDisabled_throwsBadRequest() {
        ChatbotConfig config = new ChatbotConfig();
        config.setOrganizationId(orgId);
        config.setEnabled(false);

        when(organizationRepository.findBySlug("bole-trading")).thenReturn(Optional.of(organization));
        when(chatbotConfigRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(config));

        UUID sessionId = UUID.randomUUID();
        PublicChatMessageRequest req = new PublicChatMessageRequest(
                sessionId,
                "Hello",
                null, null, null, orgId);

        assertThatThrownBy(() -> aiService.handlePublicChat("bole-trading", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void handlePublicChat_success_savesMessagesAndReturnsReply() {
        ChatbotConfig config = new ChatbotConfig();
        config.setOrganizationId(orgId);
        config.setEnabled(true);

        UUID sessionId = UUID.randomUUID();
        ChatbotConversation conv = new ChatbotConversation();
        conv.setId(UUID.randomUUID());
        conv.setOrganizationId(orgId);
        conv.setSessionId(sessionId.toString());

        when(organizationRepository.findBySlug("bole-trading")).thenReturn(Optional.of(organization));
        when(chatbotConfigRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(config));
        when(conversationRepository.findByOrganizationIdAndSessionId(orgId, sessionId.toString())).thenReturn(Optional.of(conv));
        when(conversationRepository.save(any(ChatbotConversation.class))).thenAnswer(i -> i.getArgument(0));
        when(messageRepository.save(any(ChatbotMessage.class))).thenAnswer(i -> i.getArgument(0));
        when(aiProvider.generateChatReply(any(), any(), any(), any(), any())).thenReturn("Welcome to Bole Trading! How can I help?");

        PublicChatMessageRequest req = new PublicChatMessageRequest(
                sessionId,
                "Tell me about your services",
                "Abebe Kebede",
                "abebe@example.com",
                "+251911223344",
                orgId);

        PublicChatMessageResponse resp = aiService.handlePublicChat("bole-trading", req);

        assertThat(resp.role()).isEqualTo("ASSISTANT");
        assertThat(resp.content()).contains("Welcome to Bole Trading!");
        verify(leadRepository).save(any(Lead.class)); // Auto-created lead from contact info
    }

    @Test
    void handlePublicChat_whenRateLimitExceeded_throwsBadRequest() {
        ChatbotConfig config = new ChatbotConfig();
        config.setOrganizationId(orgId);
        config.setEnabled(true);

        when(organizationRepository.findBySlug("bole-trading")).thenReturn(Optional.of(organization));
        when(chatbotConfigRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(config));
        UUID busySessionId = UUID.randomUUID();
        when(messageRepository.countByConversationIdAndCreatedAtAfter(eq(busySessionId), any()))
                .thenReturn(5L);

        PublicChatMessageRequest req = new PublicChatMessageRequest(
                busySessionId,
                "Spamming messages",
                null, null, null,
                orgId);

        assertThatThrownBy(() -> aiService.handlePublicChat("bole-trading", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Rate limit exceeded");
    }
}
