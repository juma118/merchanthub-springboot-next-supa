package com.merchanthub.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_logs")
@Getter
@Setter
public class SyncLog {

    public static final String TYPE_PULL = "pull_sync";
    public static final String TYPE_WEBHOOK = "webhook";
    public static final String RUNNING = "running";
    public static final String SUCCESS = "success";
    public static final String FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String status = RUNNING;

    private String detail;

    @Column(name = "records_processed", nullable = false)
    private int recordsProcessed = 0;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
