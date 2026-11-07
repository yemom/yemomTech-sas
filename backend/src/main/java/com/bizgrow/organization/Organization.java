package com.bizgrow.organization;

import com.bizgrow.common.entity.TimestampedEntity;
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

import java.util.UUID;

/**
 * The tenant. Every tenant-owned row references this table's id; there is no
 * separate "tenant" concept to keep in sync.
 *
 * <p>Ethiopian defaults ({@code ET}/{@code ETB}/{@code Africa/Addis_Ababa}) are
 * column defaults, not logic constants — services read/write them per organization,
 * so international expansion is configuration, not refactoring.</p>
 */
@Entity
@Table(name = "organizations", uniqueConstraints = @UniqueConstraint(name = "uq_organizations_slug", columnNames = "slug"))
@Getter
@Setter
@NoArgsConstructor
public class Organization extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** URL-facing identifier, unique and generated at creation (never user-chosen). */
    @Column(name = "slug", nullable = false, length = 220)
    private String slug;

    @Column(name = "description", length = 2000)
    private String description;

    /** Free-form normalized label (e.g. DIGITAL_MARKETING, REAL_ESTATE). */
    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode = "ET";

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "ETB";

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Africa/Addis_Ababa";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    @Column(name = "created_by")
    private UUID createdBy;
}
