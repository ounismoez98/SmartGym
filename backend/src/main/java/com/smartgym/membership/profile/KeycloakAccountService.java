package com.smartgym.membership.profile;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class KeycloakAccountService {
    private final RestClient restClient;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakAccountService(
            RestClient.Builder restClientBuilder,
            @Value("${app.keycloak.admin.base-url}") String baseUrl,
            @Value("${app.keycloak.admin.realm:gym}") String realm,
            @Value("${app.keycloak.admin.client-id:smartgym-provisioner}") String clientId,
            @Value("${app.keycloak.admin.client-secret:}") String clientSecret) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String createAccount(
            String username,
            String password,
            String firstName,
            String lastName,
            String email,
            String role) {
        String token = accessToken();
        URI userLocation;
        try {
            userLocation = restClient.post()
                    .uri("/admin/realms/{realm}/users", realm)
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "username", username,
                            "enabled", true,
                            "emailVerified", true,
                            "firstName", firstName,
                            "lastName", lastName,
                            "email", email,
                            "credentials", List.of(Map.of(
                                    "type", "password",
                                    "value", password,
                                    "temporary", true))))
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();
        } catch (RestClientResponseException exception) {
            throw responseFailure(exception, "Keycloak could not create the account.");
        } catch (RestClientException exception) {
            throw unavailable("Keycloak account service is unavailable.", exception);
        }

        if (userLocation == null || userLocation.getPath() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Keycloak created an account without returning its identifier.");
        }
        String userId = userLocation.getPath().substring(userLocation.getPath().lastIndexOf('/') + 1);
        try {
            RoleRepresentation roleRepresentation = restClient.get()
                    .uri("/admin/realms/{realm}/roles/{role}", realm, role)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(RoleRepresentation.class);
            if (roleRepresentation == null || roleRepresentation.id() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak role was not returned.");
            }
            restClient.post()
                    .uri("/admin/realms/{realm}/users/{userId}/role-mappings/realm", realm, userId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(roleRepresentation))
                    .retrieve()
                    .toBodilessEntity();
            return userId;
        } catch (RuntimeException exception) {
            deleteCreatedUser(token, userId, exception);
            if (exception instanceof RestClientResponseException responseException) {
                throw responseFailure(responseException, "Keycloak could not assign the account role.");
            }
            if (exception instanceof RestClientException clientException) {
                throw unavailable("Keycloak account service is unavailable.", clientException);
            }
            throw exception;
        }
    }

    public void deleteAccount(String userId, RuntimeException cause) {
        try {
            deleteCreatedUser(accessToken(), userId, cause);
        } catch (RuntimeException cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }
    }

    private String accessToken() {
        if (clientSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Keycloak account provisioning is not configured.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        try {
            TokenResponse response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Keycloak did not return a service access token.");
            }
            return response.accessToken();
        } catch (RestClientResponseException exception) {
            throw responseFailure(exception, "Keycloak service authentication failed.");
        } catch (RestClientException exception) {
            throw unavailable("Keycloak account service is unavailable.", exception);
        }
    }

    private void deleteCreatedUser(String token, String userId, RuntimeException cause) {
        try {
            restClient.delete()
                    .uri("/admin/realms/{realm}/users/{userId}", realm, userId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }
    }

    private static ResponseStatusException responseFailure(
            RestClientResponseException exception, String message) {
        HttpStatus status = exception.getStatusCode().value() == 409
                ? HttpStatus.CONFLICT
                : exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403
                        ? HttpStatus.SERVICE_UNAVAILABLE
                        : HttpStatus.BAD_GATEWAY;
        return new ResponseStatusException(status, message);
    }

    private static ResponseStatusException unavailable(String message, RestClientException cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    private record RoleRepresentation(String id, String name) {}
}
