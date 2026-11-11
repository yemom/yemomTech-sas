package com.YemomBizGrow.billing;

import com.YemomBizGrow.billing.dto.BillingHistoryResponse;
import com.YemomBizGrow.billing.dto.InitializePaymentRequest;
import com.YemomBizGrow.billing.dto.InitializePaymentResponse;
import com.YemomBizGrow.billing.dto.OrganizationSubscriptionResponse;
import com.YemomBizGrow.billing.dto.PaymentTransactionResponse;
import com.YemomBizGrow.billing.dto.SubscriptionPlanResponse;
import com.YemomBizGrow.billing.dto.VerifyPaymentResponse;
import com.YemomBizGrow.common.response.PageResponse;
import com.YemomBizGrow.common.security.AuthenticatedUser;
import com.YemomBizGrow.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    /**
     * Public / authenticated: lists available subscription plans.
     */
    @GetMapping("/billing/plans")
    public ResponseEntity<List<SubscriptionPlanResponse>> getPlans() {
        return ResponseEntity.ok(billingService.getPlans());
    }

    /**
     * Gets current organization subscription and plan entitlement limits.
     */
    @GetMapping("/organizations/{orgId}/billing/subscription")
    public ResponseEntity<OrganizationSubscriptionResponse> getSubscription(
            @PathVariable UUID orgId
    ) {
        UUID actorUserId = CurrentUser.require().id();
        return ResponseEntity.ok(billingService.getSubscription(orgId, actorUserId));
    }

    /**
     * Starts payment checkout session for plan upgrade.
     */
    @PostMapping("/organizations/{orgId}/billing/checkout")
    public ResponseEntity<InitializePaymentResponse> initializePayment(
            @PathVariable UUID orgId,
            @Valid @RequestBody InitializePaymentRequest request
    ) {
        AuthenticatedUser user = CurrentUser.require();
        InitializePaymentResponse response = billingService.initializePayment(
                orgId, request, user.id(), user.email()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Verifies payment completion after customer returns from Chapa checkout.
     */
    @GetMapping("/organizations/{orgId}/billing/verify/{txRef}")
    public ResponseEntity<VerifyPaymentResponse> verifyPayment(
            @PathVariable UUID orgId,
            @PathVariable String txRef
    ) {
        UUID actorUserId = CurrentUser.require().id();
        return ResponseEntity.ok(billingService.verifyPayment(orgId, txRef, actorUserId));
    }

    /**
     * Schedules subscription cancellation at current period end.
     */
    @PostMapping("/organizations/{orgId}/billing/cancel")
    public ResponseEntity<OrganizationSubscriptionResponse> cancelSubscription(
            @PathVariable UUID orgId
    ) {
        UUID actorUserId = CurrentUser.require().id();
        return ResponseEntity.ok(billingService.cancelSubscription(orgId, actorUserId));
    }

    /**
     * Lists paginated payment transactions for an organization.
     */
    @GetMapping("/organizations/{orgId}/billing/transactions")
    public ResponseEntity<PageResponse<PaymentTransactionResponse>> getTransactions(
            @PathVariable UUID orgId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID actorUserId = CurrentUser.require().id();
        return ResponseEntity.ok(PageResponse.from(billingService.getTransactions(orgId, actorUserId, pageable)));
    }

    /**
     * Lists paginated billing event audit history for an organization.
     */
    @GetMapping("/organizations/{orgId}/billing/history")
    public ResponseEntity<PageResponse<BillingHistoryResponse>> getHistory(
            @PathVariable UUID orgId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID actorUserId = CurrentUser.require().id();
        return ResponseEntity.ok(PageResponse.from(billingService.getHistory(orgId, actorUserId, pageable)));
    }

    /**
     * Public webhook receiver for Chapa payment events.
     */
    @PostMapping("/public/billing/chapa/webhook")
    public ResponseEntity<String> chapaWebhook(
            @RequestHeader(value = "x-chapa-signature", required = false) String signature,
            @RequestBody String rawBody
    ) {
        billingService.handleWebhook(signature, rawBody);
        return ResponseEntity.ok("Webhook processed");
    }
}
