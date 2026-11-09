package com.YemomBizGrow.billing;

import com.YemomBizGrow.common.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private OrganizationSubscription subscription;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id")
    private SubscriptionPlan plan;

    @Column(name = "tx_ref", nullable = false, unique = true, length = 255)
    private String txRef;

    @Column(name = "chapa_transaction_id", length = 255)
    private String chapaTransactionId;

    @Column(name = "amount_etb", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountEtb;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "ETB";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private PaymentProvider provider = PaymentProvider.CHAPA;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "callback_url", columnDefinition = "TEXT")
    private String callbackUrl;

    @Column(name = "return_url", columnDefinition = "TEXT")
    private String returnUrl;

    @Column(name = "initiated_by_user_id")
    private UUID initiatedByUserId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;
}
