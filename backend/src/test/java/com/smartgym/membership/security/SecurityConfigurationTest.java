package com.smartgym.membership.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityConfigurationTest {
    private final SecurityConfiguration configuration = new SecurityConfiguration();

    @Test
    void authenticationConverterKeepsOnlySupportedStringRealmRoles() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("MEMBER", "UNRELATED", 42, "ADMIN")))
                .build();

        List<String> authorities = configuration.jwtAuthenticationConverter()
                .convert(jwt)
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertEquals(List.of("ROLE_MEMBER", "ROLE_ADMIN"), authorities);
    }

    @Test
    void authenticationConverterReturnsNoAuthoritiesForMissingOrInvalidRealmRoles() {
        Jwt missingClaims = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("name", "Member")
                .build();
        Jwt invalidRoles = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", "MEMBER"))
                .build();

        assertEquals(List.of(), configuration.jwtAuthenticationConverter()
                .convert(missingClaims).getAuthorities());
        assertEquals(List.of(), configuration.jwtAuthenticationConverter()
                .convert(invalidRoles).getAuthorities());
    }
}
