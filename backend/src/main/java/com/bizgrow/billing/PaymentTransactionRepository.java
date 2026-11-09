package com.YemomBizGrow.billing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByTxRef(String txRef);

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentTransaction> findByTxRefAndOrganizationId(String txRef, UUID organizationId);

    Page<PaymentTransaction> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
}
