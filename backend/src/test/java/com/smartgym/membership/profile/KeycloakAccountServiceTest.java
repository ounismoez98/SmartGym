package com.smartgym.membership.profile;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakAccountServiceTest {
    private MockRestServiceServer server;
    private KeycloakAccountService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new KeycloakAccountService(builder, "http://keycloak:8080", "gym",
                "smartgym-provisioner", "test-secret");
    }

    @Test
    void createsUserAssignsRoleAndReturnsKeycloakGeneratedId() {
        expectToken();
        server.expect(requestTo("http://keycloak:8080/admin/realms/gym/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"temporary\":true")))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(URI.create("http://keycloak:8080/admin/realms/gym/users/generated-id")));
        server.expect(requestTo("http://keycloak:8080/admin/realms/gym/roles/MEMBER"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"role-id\",\"name\":\"MEMBER\"}",
                        org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://keycloak:8080/admin/realms/gym/users/generated-id/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[{\"id\":\"role-id\",\"name\":\"MEMBER\"}]"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(service.createAccount("new-member", "temporary-password", "New", "Member",
                "new@example.test", "MEMBER")).isEqualTo("generated-id");
        server.verify();
    }

    @Test
    void deletesNewUserIfRoleAssignmentFails() {
        expectToken();
        server.expect(requestTo("http://keycloak:8080/admin/realms/gym/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(URI.create("http://keycloak:8080/admin/realms/gym/users/generated-id")));
        server.expect(requestTo("http://keycloak:8080/admin/realms/gym/roles/COACH"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo("http://keycloak:8080/admin/realms/gym/users/generated-id"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatThrownBy(() -> service.createAccount("new-coach", "temporary-password", "New", "Coach",
                "new-coach@example.test", "COACH"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    private void expectToken() {
        server.expect(requestTo("http://keycloak:8080/realms/gym/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=smartgym-provisioner")))
                .andRespond(withSuccess("{\"access_token\":\"service-token\"}",
                        org.springframework.http.MediaType.APPLICATION_JSON));
    }
}
