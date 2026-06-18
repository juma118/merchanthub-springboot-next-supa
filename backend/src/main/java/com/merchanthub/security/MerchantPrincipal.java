package com.merchanthub.security;

import java.util.UUID;

/** The authenticated tenant, exposed as the Spring Security principal. */
public record MerchantPrincipal(UUID merchantId, UUID authUserId, String email) {}
