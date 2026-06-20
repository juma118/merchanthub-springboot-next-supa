package com.merchanthub.repo;

import com.merchanthub.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /** The oldest unpublished events — the publisher's work queue. */
    List<OutboxEvent> findTop200ByPublishedAtIsNullOrderByCreatedAtAsc();
}
