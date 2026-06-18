package com.merchanthub.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class OrderEntity {

    /** Lifecycle statuses (must match the DB CHECK constraint). */
    public static final String CREATED = "created";
    public static final String PAID = "paid";
    public static final String FULFILLED = "fulfilled";
    public static final String CANCELLED = "cancelled";
    public static final String ABANDONED = "abandoned";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "USD";

    @Column(nullable = false)
    private String status = CREATED;

    @Column(name = "customer_email")
    private String customerEmail;

    // Set explicitly at ingestion time so the shop's original timestamp is kept
    // (orders are only ever created via OrderIngestionService).
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
