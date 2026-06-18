package com.merchanthub.repo;

import com.merchanthub.domain.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    // Non-null sentinels/bounds are supplied by the service so Postgres never sees
    // an untyped NULL bind parameter in an IS NULL predicate.
    @Query("""
            select o from OrderEntity o
            where o.merchantId = :mid
              and (:status = '' or o.status = :status)
              and o.createdAt >= :from
              and o.createdAt <= :to
            """)
    Page<OrderEntity> search(@Param("mid") UUID merchantId,
                             @Param("status") String status,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);

    Optional<OrderEntity> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<OrderEntity> findByMerchantIdAndExternalId(UUID merchantId, String externalId);
}
