package com.YemomBizGrow.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByTxRef(String txRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.txRef = :txRef")
    Optional<PaymentTransaction> findByTxRefForUpdate(@Param("txRef") String txRef);

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentTransaction> findByTxRefAndOrganizationId(String txRef, UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.txRef = :txRef AND pt.organizationId = :organizationId")
    Optional<PaymentTransaction> findByTxRefAndOrganizationIdForUpdate(@Param("txRef") String txRef, @Param("organizationId") UUID organizationId);

    Page<PaymentTransaction> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
}
