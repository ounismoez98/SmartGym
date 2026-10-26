package com.smartgym.training.integration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MembershipClient {
    private final RestClient restClient;

    public MembershipClient(
            RestClient.Builder builder,
            @Value("${app.membership.base-url:http://localhost:8081}") String baseUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public Identity identity(String accessToken) {
        return restClient.get()
                .uri("/api/membership/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(Identity.class);
    }

    public List<AssignedMember> assignedMembers(String accessToken) {
        List<AssignedMember> assigned = restClient.get()
                .uri("/api/membership/coach/assigned-members")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {});
        return assigned == null ? List.of() : assigned;
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    public record Identity(
            String subject,
            String displayName,
            List<String> roles,
            UUID memberProfileId,
            UUID employeeProfileId,
            String profileStatus,
            boolean onboardingRequired) {}

    public record AssignedMember(UUID memberProfileId, String displayName) {}
}
