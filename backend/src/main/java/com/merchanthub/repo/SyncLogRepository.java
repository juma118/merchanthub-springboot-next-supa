package com.merchanthub.repo;

import com.merchanthub.domain.SyncLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SyncLogRepository extends JpaRepository<SyncLog, UUID> {

    List<SyncLog> findByMerchantIdOrderByStartedAtDesc(UUID merchantId, Pageable pageable);
}
