package com.merchanthub.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Database-layer safety net. Before any {@code @Transactional} method in the
 * service layer runs its body, this advice pushes the current merchant id into
 * the Postgres session via {@code set_config('app.current_merchant_id', ..., true)}.
 *
 * <p>The RLS policies installed in V2 then constrain every statement in that
 * transaction to the merchant's own rows. If a query in the application layer
 * ever forgets its {@code WHERE merchant_id = ?} clause, the database still
 * refuses to leak another tenant's data.
 *
 * <p>Runs INSIDE the transaction because the tx interceptor is pinned to the
 * highest precedence (see {@link com.merchanthub.config.AppConfig}). The
 * {@code true} third argument makes the setting transaction-local, so it is
 * automatically reset when the pooled connection is returned.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)  // run INSIDE the transaction advice (which is HIGHEST_PRECEDENCE)
public class TenantIsolationAspect {

    @PersistenceContext
    private EntityManager em;

    @Before("@annotation(org.springframework.transaction.annotation.Transactional) "
            + "&& execution(* com.merchanthub..*(..))")
    public void applyTenantScope() {
        UUID merchantId = TenantContext.getMerchantId();
        if (merchantId != null) {
            em.createNativeQuery("select set_config('app.current_merchant_id', :id, true)")
              .setParameter("id", merchantId.toString())
              .getSingleResult();
        }
    }
}
