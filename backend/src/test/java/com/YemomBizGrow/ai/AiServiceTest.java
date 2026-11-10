package com.YemomBizGrow.ai;

import com.YemomBizGrow.ai.dto.AiRecommendationResponse;
import com.YemomBizGrow.ai.dto.ChatbotConfigRequest;
import com.YemomBizGrow.ai.dto.ChatbotConfigResponse;
import com.YemomBizGrow.ai.dto.PublicChatMessageRequest;
import com.YemomBizGrow.ai.dto.PublicChatMessageResponse;
import com.YemomBizGrow.common.exception.BadRequestException;
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
import static org.mockito.ArgumentMatchers.eq;
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
                aiProvider);

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

        when(recommendationRepository.countByOrganizationIdAndStatus(orgId, RecommendationStatus.OPEN)).thenReturn(1L);
        when(recommendationRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                eq(orgId), eq(RecommendationStatus.OPEN), any())).thenReturn(new PageImpl<>(List.of(rec)));

        PageResponse<AiRecommendationResponse> resp = aiService.getRecommendations(
                actorId, orgId, RecommendationStatus.OPEN, PageRequest.of(0, 20));

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.content().get(0).title()).isEqualTo("Fix Meta Tags");
        verify(accessControlService).requirePermission(actorId, orgId, Permission.ORG_VIEW);
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

        PublicChatMessageRequest req = new PublicChatMessageRequest("sess-1", "Hello", null, null, null);

        assertThatThrownBy(() -> aiService.handlePublicChat("bole-trading", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void handlePublicChat_success_savesMessagesAndReturnsReply() {
        ChatbotConfig config = new ChatbotConfig();
        config.setOrganizationId(orgId);
        config.setEnabled(true);

        ChatbotConversation conv = new ChatbotConversation();
        conv.setId(UUID.randomUUID());
        conv.setOrganizationId(orgId);
        conv.setSessionId("sess-100");

        when(organizationRepository.findBySlug("bole-trading")).thenReturn(Optional.of(organization));
        when(chatbotConfigRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(config));
        when(conversationRepository.findByOrganizationIdAndSessionId(orgId, "sess-100")).thenReturn(Optional.of(conv));
        when(conversationRepository.save(any(ChatbotConversation.class))).thenAnswer(i -> i.getArgument(0));
        when(messageRepository.save(any(ChatbotMessage.class))).thenAnswer(i -> i.getArgument(0));
        when(aiProvider.generateChatReply(any(), any(), any(), any(), any())).thenReturn("Welcome to Bole Trading! How can I help?");

        PublicChatMessageRequest req = new PublicChatMessageRequest(
                "sess-100",
                "Tell me about your services",
                "Abebe Kebede",
                "abebe@example.com",
                "+251911223344");

        PublicChatMessageResponse resp = aiService.handlePublicChat("bole-trading", req);

        assertThat(resp.role()).isEqualTo("ASSISTANT");
        assertThat(resp.content()).contains("Welcome to Bole Trading!");
        verify(leadRepository).save(any(Lead.class)); // Auto-created lead from contact info
    }
}
