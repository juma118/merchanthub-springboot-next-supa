package com.merchanthub.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

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
