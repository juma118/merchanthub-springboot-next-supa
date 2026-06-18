package com.merchanthub.repo;

import com.merchanthub.domain.Alert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);

    List<Alert> findByMerchantIdAndReadFalseOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);

    Optional<Alert> findByIdAndMerchantId(UUID id, UUID merchantId);

    long countByMerchantIdAndReadFalse(UUID merchantId);

    @Modifying
    @Query("update Alert a set a.read = true where a.merchantId = :mid and a.read = false")
    int markAllRead(@Param("mid") UUID merchantId);
}
