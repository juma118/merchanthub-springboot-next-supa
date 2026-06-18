package com.merchanthub.repo;

import com.merchanthub.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    // Uses an empty-string sentinel (not NULL) for "no filter": a bound NULL would
    // be untyped to Postgres and fail (lower(bytea) ...). The service passes "".
    @Query("""
            select p from Product p
            where p.merchantId = :mid
              and (:q = '' or lower(p.name) like lower(concat('%', :q, '%'))
                           or lower(p.sku) like lower(concat('%', :q, '%')))
            """)
    Page<Product> search(@Param("mid") UUID merchantId, @Param("q") String q, Pageable pageable);

    Optional<Product> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<Product> findByMerchantIdAndSku(UUID merchantId, String sku);

    Optional<Product> findByMerchantIdAndExternalId(UUID merchantId, String externalId);

    List<Product> findByMerchantId(UUID merchantId);

    boolean existsByMerchantIdAndSku(UUID merchantId, String sku);
}
