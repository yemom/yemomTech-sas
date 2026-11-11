package com.YemomBizGrow.agency;

import com.YemomBizGrow.common.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "agency_client_relationships")
@Getter
@Setter
@NoArgsConstructor
public class AgencyClientRelationship extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "agency_organization_id", nullable = false)
    private UUID agencyOrganizationId;

    @Column(name = "client_organization_id", nullable = false)
    private UUID clientOrganizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgencyRelationshipStatus status = AgencyRelationshipStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 32)
    private AgencyAccessLevel accessLevel = AgencyAccessLevel.FULL;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
