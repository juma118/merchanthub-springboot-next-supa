package com.merchanthub.repo;

import com.merchanthub.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductId(UUID productId);

    Optional<Inventory> findByMerchantIdAndProductId(UUID merchantId, UUID productId);

    List<Inventory> findByMerchantId(UUID merchantId);

    @Query("select count(i) from Inventory i where i.merchantId = :mid and i.quantity <= i.lowStockThreshold")
    long countLowStock(@Param("mid") UUID merchantId);
}
