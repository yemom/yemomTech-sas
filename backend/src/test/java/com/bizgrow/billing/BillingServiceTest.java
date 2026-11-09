package com.YemomBizGrow.billing;

import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.billing.dto.InitializePaymentRequest;
import com.YemomBizGrow.billing.dto.InitializePaymentResponse;
import com.YemomBizGrow.billing.dto.OrganizationSubscriptionResponse;
import com.YemomBizGrow.billing.dto.SubscriptionPlanResponse;
import com.YemomBizGrow.billing.dto.VerifyPaymentResponse;
import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.membership.AccessControlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private SubscriptionPlanRepository planRepository;

    @Mock
    private OrganizationSubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private BillingHistoryRepository historyRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private AuditService auditService;

    @Mock
    private ChapaClient chapaClient;

    private BillingService billingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private SubscriptionPlan freePlan;
    private SubscriptionPlan starterPlan;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(
                planRepository,
                subscriptionRepository,
                transactionRepository,
                historyRepository,
                accessControlService,
                auditService,
                chapaClient,
                objectMapper
        );

        freePlan = new SubscriptionPlan();
        freePlan.setId(UUID.randomUUID());
        freePlan.setName("Free");
        freePlan.setSlug("free");
        freePlan.setPriceEtb(BigDecimal.ZERO);
        freePlan.setMaxWebsites(1);
        freePlan.setMaxMembers(3);

        starterPlan = new SubscriptionPlan();
        starterPlan.setId(UUID.randomUUID());
        starterPlan.setName("Starter");
        starterPlan.setSlug("starter");
        starterPlan.setPriceEtb(new BigDecimal("999.00"));
        starterPlan.setMaxWebsites(3);
        starterPlan.setMaxMembers(10);
    }

    @Test
    @DisplayName("getPlans returns active subscription plans")
    void testGetPlans() {
        when(planRepository.findAllByIsActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(freePlan, starterPlan));

        List<SubscriptionPlanResponse> plans = billingService.getPlans();

        assertThat(plans).hasSize(2);
        assertThat(plans.get(0).slug()).isEqualTo("free");
        assertThat(plans.get(1).slug()).isEqualTo("starter");
    }

    @Test
    @DisplayName("getSubscription bootstraps Free plan when none exists")
    void testGetSubscriptionBootstrapsFree() {
        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.empty());
        when(planRepository.findBySlug("free")).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.save(any(OrganizationSubscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OrganizationSubscriptionResponse sub = billingService.getSubscription(orgId, userId);

        assertThat(sub).isNotNull();
        assertThat(sub.plan().slug()).isEqualTo("free");
        assertThat(sub.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("initializePayment with Free plan switches immediately")
    void testInitializeFreePlan() {
        when(planRepository.findBySlug("free")).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(OrganizationSubscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InitializePaymentRequest req = new InitializePaymentRequest("free", "/workspace/settings");
        InitializePaymentResponse resp = billingService.initializePayment(orgId, req, userId, "user@bizgrow.et");

        assertThat(resp.amountEtb()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.planName()).isEqualTo("Free");
        verify(subscriptionRepository, org.mockito.Mockito.atLeastOnce()).save(any(OrganizationSubscription.class));
    }

    @Test
    @DisplayName("initializePayment with paid plan initializes Chapa session")
    void testInitializePaidPlan() {
        when(planRepository.findBySlug("starter")).thenReturn(Optional.of(starterPlan));
        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.empty());
        when(planRepository.findBySlug("free")).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.save(any(OrganizationSubscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(chapaClient.initialize(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ChapaClient.ChapaInitializeResult(true, "https://checkout.chapa.co/checkout/test", "bg_123", "OK"));

        InitializePaymentRequest req = new InitializePaymentRequest("starter", "/workspace/billing/success");
        InitializePaymentResponse resp = billingService.initializePayment(orgId, req, userId, "user@bizgrow.et");

        assertThat(resp.checkoutUrl()).isEqualTo("https://checkout.chapa.co/checkout/test");
        assertThat(resp.amountEtb()).isEqualByComparingTo(new BigDecimal("999.00"));
        assertThat(resp.planName()).isEqualTo("Starter");
        verify(transactionRepository).save(any(PaymentTransaction.class));
    }

    @Test
    @DisplayName("verifyPayment updates subscription to new plan upon Chapa confirmation")
    void testVerifyPaymentSuccess() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID());
        tx.setOrganizationId(orgId);
        tx.setPlan(starterPlan);
        tx.setTxRef("bg_tx_123");
        tx.setAmountEtb(new BigDecimal("999.00"));
        tx.setCurrency("ETB");
        tx.setStatus(PaymentStatus.PENDING);

        OrganizationSubscription currentSub = new OrganizationSubscription();
        currentSub.setId(UUID.randomUUID());
        currentSub.setOrganizationId(orgId);
        currentSub.setPlan(freePlan);

        when(transactionRepository.findByTxRefAndOrganizationId("bg_tx_123", orgId))
                .thenReturn(Optional.of(tx));
        when(chapaClient.verify("bg_tx_123"))
                .thenReturn(new ChapaClient.ChapaVerifyResult(true, "success", "bg_tx_123", "CHAPA_REF_999", new BigDecimal("999.00"), "ETB", "Verified"));
        when(subscriptionRepository.findByOrganizationId(orgId))
                .thenReturn(Optional.of(currentSub));
        when(subscriptionRepository.save(any(OrganizationSubscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        VerifyPaymentResponse resp = billingService.verifyPayment(orgId, "bg_tx_123", userId);

        assertThat(resp.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(resp.planName()).isEqualTo("Starter");
        assertThat(currentSub.getPlan().getSlug()).isEqualTo("starter");
        assertThat(currentSub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(historyRepository, org.mockito.Mockito.atLeastOnce()).save(any(BillingHistory.class));
    }

    @Test
    @DisplayName("verifyPayment rejects payment when amount returned by Chapa does not match expected price")
    void testVerifyPaymentFailsOnAmountMismatch() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID());
        tx.setOrganizationId(orgId);
        tx.setPlan(starterPlan);
        tx.setTxRef("bg_tx_bad_amount");
        tx.setAmountEtb(new BigDecimal("999.00"));
        tx.setCurrency("ETB");
        tx.setStatus(PaymentStatus.PENDING);

        when(transactionRepository.findByTxRefAndOrganizationId("bg_tx_bad_amount", orgId))
                .thenReturn(Optional.of(tx));
        // Chapa reports only 50 ETB paid instead of 999 ETB
        when(chapaClient.verify("bg_tx_bad_amount"))
                .thenReturn(new ChapaClient.ChapaVerifyResult(true, "success", "bg_tx_bad_amount", "CHAPA_50", new BigDecimal("50.00"), "ETB", "Verified"));

        VerifyPaymentResponse resp = billingService.verifyPayment(orgId, "bg_tx_bad_amount", userId);

        assertThat(resp.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(tx.getFailureReason()).contains("amount mismatch");
        // Ensure subscription was NOT updated
        verify(subscriptionRepository, never()).save(any(OrganizationSubscription.class));
    }

    @Test
    @DisplayName("handleWebhook rejects webhook when signature is invalid")
    void testWebhookRejectsInvalidSignature() {
        when(chapaClient.validateWebhookSignature("bad-sig", "{\"status\":\"success\"}")).thenReturn(false);

        assertThatThrownBy(() -> billingService.handleWebhook("bad-sig", "{\"status\":\"success\"}"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid webhook signature");
    }

    @Test
    @DisplayName("cancelSubscription sets cancelAtPeriodEnd flag")
    void testCancelSubscription() {
        OrganizationSubscription currentSub = new OrganizationSubscription();
        currentSub.setId(UUID.randomUUID());
        currentSub.setOrganizationId(orgId);
        currentSub.setPlan(starterPlan);
        currentSub.setCancelAtPeriodEnd(false);

        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(currentSub));
        when(subscriptionRepository.save(any(OrganizationSubscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OrganizationSubscriptionResponse resp = billingService.cancelSubscription(orgId, userId);

        assertThat(resp.cancelAtPeriodEnd()).isTrue();
        assertThat(currentSub.isCancelAtPeriodEnd()).isTrue();
    }
}
