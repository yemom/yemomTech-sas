package com.YemomBizGrow.billing;

import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.billing.dto.BillingHistoryResponse;
import com.YemomBizGrow.billing.dto.InitializePaymentRequest;
import com.YemomBizGrow.billing.dto.InitializePaymentResponse;
import com.YemomBizGrow.billing.dto.OrganizationSubscriptionResponse;
import com.YemomBizGrow.billing.dto.PaymentTransactionResponse;
import com.YemomBizGrow.billing.dto.SubscriptionPlanResponse;
import com.YemomBizGrow.billing.dto.VerifyPaymentResponse;
import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final SubscriptionPlanRepository planRepository;
    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final BillingHistoryRepository historyRepository;
    private final AccessControlService accessControlService;
    private final AuditService auditService;
    private final ChapaClient chapaClient;
    private final ObjectMapper objectMapper;

    public BillingService(
            SubscriptionPlanRepository planRepository,
            OrganizationSubscriptionRepository subscriptionRepository,
            PaymentTransactionRepository transactionRepository,
            BillingHistoryRepository historyRepository,
            AccessControlService accessControlService,
            AuditService auditService,
            ChapaClient chapaClient,
            ObjectMapper objectMapper
    ) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
        this.historyRepository = historyRepository;
        this.accessControlService = accessControlService;
        this.auditService = auditService;
        this.chapaClient = chapaClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all active subscription plans.
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getPlans() {
        return planRepository.findAllByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(SubscriptionPlanResponse::from)
                .toList();
    }

    /**
     * Retrieves the current organization subscription.
     * If no record exists, bootstraps a Free plan subscription.
     */
    @Transactional
    public OrganizationSubscriptionResponse getSubscription(UUID orgId, UUID actorUserId) {
        accessControlService.requirePermission(actorUserId, orgId, Permission.ORG_VIEW);

        OrganizationSubscription subscription = subscriptionRepository.findByOrganizationId(orgId)
                .orElseGet(() -> createDefaultFreeSubscription(orgId));

        return OrganizationSubscriptionResponse.from(subscription);
    }

    /**
     * Initializes a payment checkout session with Chapa or immediate free tier activation.
     */
    @Transactional
    public InitializePaymentResponse initializePayment(
            UUID orgId,
            InitializePaymentRequest request,
            UUID actorUserId,
            String actorEmail
    ) {
        accessControlService.requirePermission(actorUserId, orgId, Permission.ORG_UPDATE);

        SubscriptionPlan targetPlan = planRepository.findBySlug(request.planSlug())
                .orElseThrow(() -> new NotFoundException("Plan not found: " + request.planSlug()));

        // If selecting Free plan, switch immediately without payment gateway
        if (targetPlan.getPriceEtb().compareTo(BigDecimal.ZERO) == 0) {
            OrganizationSubscription sub = subscriptionRepository.findByOrganizationId(orgId)
                    .orElseGet(() -> createDefaultFreeSubscription(orgId));
            sub.setPlan(targetPlan);
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setCurrentPeriodStart(Instant.now());
            sub.setCurrentPeriodEnd(null);
            subscriptionRepository.save(sub);

            recordBillingHistory(orgId, null, sub.getId(), BillingEventType.PLAN_CHANGED,
                    "Switched to Free plan", BigDecimal.ZERO);

            auditService.record(actorUserId, orgId, AuditAction.SUBSCRIPTION_UPGRADED,
                    "SubscriptionPlan", targetPlan.getId(),
                    Map.of("plan", targetPlan.getSlug(), "amount", 0));

            return new InitializePaymentResponse(
                    request.returnUrl() != null ? request.returnUrl() : "/workspace/settings",
                    "free_plan_switch",
                    BigDecimal.ZERO,
                    "ETB",
                    targetPlan.getName()
            );
        }

        // Generate a cryptographically unique transaction reference
        String txRef = "bg_" + orgId.toString().substring(0, 8) + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);

        OrganizationSubscription currentSub = subscriptionRepository.findByOrganizationId(orgId)
                .orElseGet(() -> createDefaultFreeSubscription(orgId));

        PaymentTransaction tx = new PaymentTransaction();
        tx.setOrganizationId(orgId);
        tx.setSubscription(currentSub);
        tx.setPlan(targetPlan);
        tx.setTxRef(txRef);
        tx.setAmountEtb(targetPlan.getPriceEtb());
        tx.setCurrency("ETB");
        tx.setStatus(PaymentStatus.PENDING);
        tx.setProvider(PaymentProvider.CHAPA);
        tx.setInitiatedByUserId(actorUserId);
        tx.setReturnUrl(request.returnUrl());
        tx.setIdempotencyKey(txRef);

        ChapaClient.ChapaInitializeResult initResult = chapaClient.initialize(
                targetPlan.getPriceEtb(),
                "ETB",
                actorEmail,
                "YemomBizGrow",
                "Customer",
                txRef,
                null,
                request.returnUrl(),
                "YemomBizGrow " + targetPlan.getName() + " Plan",
                "Subscription for " + targetPlan.getName() + " (" + targetPlan.getPriceEtb() + " ETB)"
        );

        if (!initResult.success()) {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setFailureReason(initResult.message());
            transactionRepository.save(tx);
            throw new BadRequestException("Failed to initialize Chapa payment: " + initResult.message());
        }

        tx.setCheckoutUrl(initResult.checkoutUrl());
        transactionRepository.save(tx);

        auditService.record(actorUserId, orgId, AuditAction.PAYMENT_INITIALIZED,
                "PaymentTransaction", tx.getId(),
                Map.of("txRef", txRef, "plan", targetPlan.getSlug(), "amount", targetPlan.getPriceEtb().doubleValue()));

        return new InitializePaymentResponse(
                initResult.checkoutUrl(),
                txRef,
                targetPlan.getPriceEtb(),
                "ETB",
                targetPlan.getName()
        );
    }

    /**
     * Verifies payment completion server-side via Chapa verification API.
     * Enforces amount and currency equality.
     */
    @Transactional
    public VerifyPaymentResponse verifyPayment(UUID orgId, String txRef, UUID actorUserId) {
        accessControlService.requirePermission(actorUserId, orgId, Permission.ORG_VIEW);

        PaymentTransaction tx = transactionRepository.findByTxRefAndOrganizationIdForUpdate(txRef, orgId)
                .orElseGet(() -> transactionRepository.findByTxRefAndOrganizationId(txRef, orgId)
                        .orElseThrow(() -> new NotFoundException("Payment transaction not found: " + txRef)));

        // Idempotency check: if already SUCCESS, return cached success
        if (tx.getStatus() == PaymentStatus.SUCCESS) {
            return new VerifyPaymentResponse(
                    tx.getTxRef(),
                    PaymentStatus.SUCCESS,
                    tx.getAmountEtb(),
                    tx.getCurrency(),
                    tx.getPlan() != null ? tx.getPlan().getName() : "Subscription",
                    tx.getVerifiedAt(),
                    "Payment already verified and subscription is active"
            );
        }

        ChapaClient.ChapaVerifyResult verifyResult = chapaClient.verify(txRef);

        // Strict fail-closed verification
        if (!verifyResult.success() || !"success".equalsIgnoreCase(verifyResult.status())) {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setFailureReason("Provider verification failed: " + verifyResult.message());
            transactionRepository.save(tx);
            recordBillingHistory(orgId, tx.getId(), null, BillingEventType.PAYMENT_FAILED,
                    "Payment failed: " + verifyResult.message(), tx.getAmountEtb());
            auditService.record(actorUserId, orgId, AuditAction.PAYMENT_FAILED,
                    "PaymentTransaction", tx.getId(),
                    Map.of("txRef", txRef, "reason", verifyResult.message()));
            return new VerifyPaymentResponse(txRef, PaymentStatus.FAILED, tx.getAmountEtb(), tx.getCurrency(),
                    tx.getPlan() != null ? tx.getPlan().getName() : "Subscription", null,
                    "Payment verification failed: " + verifyResult.message());
        }

        if (verifyResult.amount() == null || verifyResult.amount().compareTo(tx.getAmountEtb()) != 0) {
            tx.setStatus(PaymentStatus.FAILED);
            String msg = "Payment amount mismatch or missing: expected " + tx.getAmountEtb() + " but got " + verifyResult.amount();
            tx.setFailureReason(msg);
            transactionRepository.save(tx);
            log.warn("Security Alert: {}", msg);
            return new VerifyPaymentResponse(txRef, PaymentStatus.FAILED, tx.getAmountEtb(), tx.getCurrency(),
                    tx.getPlan().getName(), null, msg);
        }

        if (verifyResult.currency() == null || !verifyResult.currency().equalsIgnoreCase(tx.getCurrency())) {
            tx.setStatus(PaymentStatus.FAILED);
            String msg = "Payment currency mismatch or missing: expected " + tx.getCurrency() + " but got " + verifyResult.currency();
            tx.setFailureReason(msg);
            transactionRepository.save(tx);
            log.warn("Security Alert: {}", msg);
            return new VerifyPaymentResponse(txRef, PaymentStatus.FAILED, tx.getAmountEtb(), tx.getCurrency(),
                    tx.getPlan().getName(), null, msg);
        }

        if (verifyResult.txRef() == null || !verifyResult.txRef().equals(tx.getTxRef())) {
            tx.setStatus(PaymentStatus.FAILED);
            String msg = "Payment txRef mismatch: expected " + tx.getTxRef() + " but got " + verifyResult.txRef();
            tx.setFailureReason(msg);
            transactionRepository.save(tx);
            log.warn("Security Alert: {}", msg);
            return new VerifyPaymentResponse(txRef, PaymentStatus.FAILED, tx.getAmountEtb(), tx.getCurrency(),
                    tx.getPlan().getName(), null, msg);
        }

        if (verifyResult.chapaTransactionId() == null || verifyResult.chapaTransactionId().isBlank()) {
            tx.setStatus(PaymentStatus.FAILED);
            String msg = "Payment provider transaction identity missing or invalid";
            tx.setFailureReason(msg);
            transactionRepository.save(tx);
            log.warn("Security Alert: {}", msg);
            return new VerifyPaymentResponse(txRef, PaymentStatus.FAILED, tx.getAmountEtb(), tx.getCurrency(),
                    tx.getPlan().getName(), null, msg);
        }

        // All validation passed — mark as successful and activate subscription
        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setVerifiedAt(Instant.now());
        tx.setChapaTransactionId(verifyResult.chapaTransactionId() != null ? verifyResult.chapaTransactionId() : txRef);
        transactionRepository.save(tx);

        // Update organization subscription
        OrganizationSubscription sub = subscriptionRepository.findByOrganizationId(orgId)
                .orElseGet(() -> createDefaultFreeSubscription(orgId));

        sub.setPlan(tx.getPlan());
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodStart(Instant.now());
        sub.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
        sub.setCancelledAt(null);
        sub.setCancelAtPeriodEnd(false);
        subscriptionRepository.save(sub);

        recordBillingHistory(orgId, tx.getId(), sub.getId(), BillingEventType.PAYMENT_SUCCESS,
                "Payment of " + tx.getAmountEtb() + " ETB succeeded via Chapa. Upgraded to " + tx.getPlan().getName(),
                tx.getAmountEtb());

        recordBillingHistory(orgId, tx.getId(), sub.getId(), BillingEventType.SUBSCRIPTION_UPGRADED,
                "Subscription upgraded to " + tx.getPlan().getName() + " plan",
                tx.getAmountEtb());

        auditService.record(actorUserId, orgId, AuditAction.PAYMENT_COMPLETED,
                "PaymentTransaction", tx.getId(),
                Map.of("txRef", txRef, "plan", tx.getPlan().getSlug(), "amount", tx.getAmountEtb().doubleValue()));
        auditService.record(actorUserId, orgId, AuditAction.SUBSCRIPTION_UPGRADED,
                "SubscriptionPlan", tx.getPlan().getId(),
                Map.of("newPlan", tx.getPlan().getSlug()));

        return new VerifyPaymentResponse(
                tx.getTxRef(),
                PaymentStatus.SUCCESS,
                tx.getAmountEtb(),
                tx.getCurrency(),
                tx.getPlan().getName(),
                tx.getVerifiedAt(),
                "Payment verified successfully! Welcome to the " + tx.getPlan().getName() + " plan."
        );
    }

    /**
     * Handles webhook notification from Chapa with signature and payload verification.
     */
    @Transactional
    public void handleWebhook(String signature, String rawBody) {
        if (!chapaClient.validateWebhookSignature(signature, rawBody)) {
            log.warn("Invalid Chapa webhook signature received");
            throw new BadRequestException("Invalid webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String extractedRef = root.path("data").path("tx_ref").asText("");
            final String webhookTxRef = extractedRef.isBlank() ? root.path("tx_ref").asText("") : extractedRef;

            if (webhookTxRef.isBlank()) {
                log.warn("Chapa webhook missing tx_ref in payload: {}", rawBody);
                return;
            }

            PaymentTransaction tx = transactionRepository.findByTxRefForUpdate(webhookTxRef)
                    .orElseGet(() -> transactionRepository.findByTxRef(webhookTxRef).orElse(null));
            if (tx == null) {
                log.warn("Chapa webhook received for unknown txRef: {}", webhookTxRef);
                return;
            }

            // Webhook replay attack protection / idempotency & state downgrade prevention
            if (tx.getStatus() == PaymentStatus.SUCCESS) {
                log.info("Chapa webhook txRef {} already marked SUCCESS, skipping duplicate activation", webhookTxRef);
                return;
            }

            // Server-side verification with Chapa before activating subscription
            ChapaClient.ChapaVerifyResult verifyResult = chapaClient.verify(webhookTxRef);
            if (!verifyResult.success() || !"success".equalsIgnoreCase(verifyResult.status())) {
                log.warn("Chapa server verification failed for webhook txRef {}: {}", webhookTxRef, verifyResult.message());
                tx.setStatus(PaymentStatus.FAILED);
                tx.setFailureReason("Server verification failed: " + verifyResult.message());
                transactionRepository.save(tx);
                return;
            }

            // Fail-closed: require valid, non-null, exact amount from server verification
            if (verifyResult.amount() == null || verifyResult.amount().compareTo(tx.getAmountEtb()) != 0) {
                log.error("Server verified amount mismatch or missing for txRef {}: expected {} but got {}", webhookTxRef, tx.getAmountEtb(), verifyResult.amount());
                tx.setStatus(PaymentStatus.FAILED);
                tx.setFailureReason("Verified amount mismatch or missing");
                transactionRepository.save(tx);
                return;
            }

            // Fail-closed: require valid, non-blank, exact currency from server verification
            if (verifyResult.currency() == null || !verifyResult.currency().equalsIgnoreCase(tx.getCurrency())) {
                log.error("Server verified currency mismatch or missing for txRef {}: expected {} but got {}", webhookTxRef, tx.getCurrency(), verifyResult.currency());
                tx.setStatus(PaymentStatus.FAILED);
                tx.setFailureReason("Verified currency mismatch or missing");
                transactionRepository.save(tx);
                return;
            }

            // Fail-closed: verify matching txRef
            if (verifyResult.txRef() == null || !verifyResult.txRef().equals(tx.getTxRef())) {
                log.error("Server verified txRef mismatch for txRef {}: got {}", webhookTxRef, verifyResult.txRef());
                tx.setStatus(PaymentStatus.FAILED);
                tx.setFailureReason("Verified txRef mismatch");
                transactionRepository.save(tx);
                return;
            }

            // Fail-closed: verify provider transaction identity
            if (verifyResult.chapaTransactionId() == null || verifyResult.chapaTransactionId().isBlank()) {
                log.error("Server verified chapaTransactionId missing for txRef {}", webhookTxRef);
                tx.setStatus(PaymentStatus.FAILED);
                tx.setFailureReason("Verified provider transaction ID missing");
                transactionRepository.save(tx);
                return;
            }

            tx.setStatus(PaymentStatus.SUCCESS);
            tx.setVerifiedAt(Instant.now());
            tx.setChapaTransactionId(verifyResult.chapaTransactionId() != null ? verifyResult.chapaTransactionId() : root.path("data").path("reference").asText(webhookTxRef));
            transactionRepository.save(tx);

            OrganizationSubscription sub = subscriptionRepository.findByOrganizationId(tx.getOrganizationId())
                    .orElseGet(() -> createDefaultFreeSubscription(tx.getOrganizationId()));

            sub.setPlan(tx.getPlan());
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setCurrentPeriodStart(Instant.now());
            sub.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
            subscriptionRepository.save(sub);

            recordBillingHistory(tx.getOrganizationId(), tx.getId(), sub.getId(), BillingEventType.PAYMENT_SUCCESS,
                    "Webhook confirmed payment of " + tx.getAmountEtb() + " ETB for " + tx.getPlan().getName(),
                    tx.getAmountEtb());

            log.info("Chapa webhook successfully verified and applied payment for txRef: {}", webhookTxRef);

        } catch (Exception e) {
            log.error("Failed to parse Chapa webhook payload", e);
            throw new BadRequestException("Malformed webhook payload");
        }
    }

    /**
     * Cancels an active organization subscription at the end of the billing period.
     */
    @Transactional
    public OrganizationSubscriptionResponse cancelSubscription(UUID orgId, UUID actorUserId) {
        accessControlService.requirePermission(actorUserId, orgId, Permission.ORG_UPDATE);

        OrganizationSubscription sub = subscriptionRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new NotFoundException("No active subscription found"));

        sub.setCancelAtPeriodEnd(true);
        sub.setCancelledAt(Instant.now());
        OrganizationSubscription saved = subscriptionRepository.save(sub);

        recordBillingHistory(orgId, null, sub.getId(), BillingEventType.SUBSCRIPTION_CANCELLED,
                "Subscription scheduled for cancellation at period end", BigDecimal.ZERO);

        auditService.record(actorUserId, orgId, AuditAction.SUBSCRIPTION_CANCELLED,
                "OrganizationSubscription", sub.getId(),
                Map.of("plan", sub.getPlan().getSlug()));

        return OrganizationSubscriptionResponse.from(saved);
    }

    /**
     * Lists paginated payment transactions for an organization.
     */
    @Transactional(readOnly = true)
    public Page<PaymentTransactionResponse> getTransactions(UUID orgId, UUID actorUserId, Pageable pageable) {
        accessControlService.requirePermission(actorUserId, orgId, Permission.ORG_VIEW);
        return transactionRepository.findAllByOrganizationIdOrderByCreatedAtDesc(orgId, pageable)
                .map(PaymentTransactionResponse::from);
    }

    /**
     * Lists paginated billing event history for an organization.
     */
    @Transactional(readOnly = true)
    public Page<BillingHistoryResponse> getHistory(UUID orgId, UUID actorUserId, Pageable pageable) {
        accessControlService.requirePermission(actorUserId, orgId, Permission.ORG_VIEW);
        return historyRepository.findAllByOrganizationIdOrderByCreatedAtDesc(orgId, pageable)
                .map(BillingHistoryResponse::from);
    }

    // --- Internal Helpers ---

    private OrganizationSubscription createDefaultFreeSubscription(UUID orgId) {
        SubscriptionPlan freePlan = planRepository.findBySlug("free")
                .orElseGet(() -> {
                    SubscriptionPlan defaultPlan = new SubscriptionPlan();
                    defaultPlan.setName("Free");
                    defaultPlan.setSlug("free");
                    defaultPlan.setDescription("Default free tier");
                    defaultPlan.setPriceEtb(BigDecimal.ZERO);
                    defaultPlan.setBillingInterval(BillingInterval.MONTHLY);
                    defaultPlan.setMaxWebsites(1);
                    defaultPlan.setMaxMembers(3);
                    defaultPlan.setMaxLeads(200);
                    defaultPlan.setMaxCampaigns(1);
                    return planRepository.save(defaultPlan);
                });

        OrganizationSubscription sub = new OrganizationSubscription();
        sub.setOrganizationId(orgId);
        sub.setPlan(freePlan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodStart(Instant.now());
        return subscriptionRepository.save(sub);
    }

    private void recordBillingHistory(
            UUID orgId,
            UUID transactionId,
            UUID subscriptionId,
            BillingEventType eventType,
            String description,
            BigDecimal amountEtb
    ) {
        BillingHistory h = new BillingHistory();
        h.setOrganizationId(orgId);
        h.setTransactionId(transactionId);
        h.setSubscriptionId(subscriptionId);
        h.setEventType(eventType);
        h.setDescription(description);
        h.setAmountEtb(amountEtb);
        historyRepository.save(h);
    }
}