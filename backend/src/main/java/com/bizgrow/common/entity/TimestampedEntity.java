package com.bizgrow.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * UTC instants maintained by the entity lifecycle, mirroring the
 * {@code created_at}/{@code updated_at} columns that every business table carries.
 *
 * <p>{@link Instant} + {@code TIMESTAMP WITH TIME ZONE} keeps storage timezone-free
 * (always UTC); conversion to {@code Africa/Addis_Ababa} happens only at display time
 * using the organization's configured timezone.</p>
 */
@MappedSuperclass
@Getter
@Setter
public abstract class TimestampedEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        setCreatedAt(now);
        setUpdatedAt(now);
    }

    @PreUpdate
    protected void onUpdate() {
        setUpdatedAt(Instant.now());
    }
}
