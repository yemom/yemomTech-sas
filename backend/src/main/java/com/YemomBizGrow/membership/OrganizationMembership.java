package com.YemomBizGrow.membership;

import com.YemomBizGrow.common.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The ONLY authoritative link between a user and a tenant.
 *
 * <p>Every tenant-scoped operation re-reads this row (via the access-control
 * service) instead of trusting JWT claims or client-sent organization ids — this
 * is what makes cross-tenant access structurally impossible rather than merely
 * hidden in the UI.</p>
 */
@Entity
@Table(name = "organization_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_org_member_user", columnNames = {"organization_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class OrganizationMembership extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_status", nullable = false, length = 32)
    private MembershipStatus membershipStatus = MembershipStatus.ACTIVE;

    /** When the invitation was accepted / direct membership created. */
    @Column(name = "joined_at")
    private Instant joinedAt;
}
