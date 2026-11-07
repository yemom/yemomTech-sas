package com.bizgrow.website;

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
 * A website registered by an organization (first vertical slice).
 *
 * <p>Metadata only: {@code url} was validated and normalized by
 * {@link com.bizgrow.common.util.WebsiteUrlNormalizer} but is never fetched —
 * registration performs no outbound request, so it cannot be used for SSRF.
 * {@code domain} is unique per organization ({@code uq_websites_org_domain}).</p>
 */
@Entity
@Table(name = "websites",
        uniqueConstraints = @UniqueConstraint(name = "uq_websites_org_domain", columnNames = {"organization_id", "domain"}))
@Getter
@Setter
@NoArgsConstructor
public class Website extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Normalized host, lowercase, no scheme/port/path (e.g. "example.com"). */
    @Column(name = "domain", nullable = false, length = 253)
    private String domain;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WebsiteStatus status = WebsiteStatus.ACTIVE;

    @Column(name = "created_by")
    private UUID createdBy;
}
