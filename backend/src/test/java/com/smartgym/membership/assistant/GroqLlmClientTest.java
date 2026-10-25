package com.smartgym.membership.assistant;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroqLlmClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retries429OnceAndSendsOpenAiJsonContract() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger jsonMode = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if ("Bearer test-placeholder".equals(authorization)
                    && requestBody.contains("\"response_format\":{\"type\":\"json_object\"}")) {
                jsonMode.incrementAndGet();
            }
            if (requests.get() == 1) {
                respond(exchange, 429, "{}");
            } else {
                respond(exchange, 200, """
                        {"choices":[{"message":{"content":"{\\"subject\\":\\"Renewal\\",\\"body\\":\\"Text\\"}"}}]}
                        """);
            }
        });

        String content = client(5).complete("system", "user", true);

        assertThat(content).contains("\"subject\":\"Renewal\"");
        assertThat(requests.get()).isEqualTo(2);
        assertThat(jsonMode.get()).isEqualTo(2);
    }

    @Test
    void reportsPersistent429AsClearAdminErrorAfterOneRetry() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 429, "{}");
        });

        assertThatThrownBy(() -> client(5).complete("system", "user", false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getReason())
                        .contains("rate limit persisted after one retry"));
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void reportsProvider5xxAsClearAdminError() throws IOException {
        startServer(exchange -> respond(exchange, 503, "{}"));

        assertThatThrownBy(() -> client(5).complete("system", "user", false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getReason())
                        .contains("temporarily unavailable"));
    }

    @Test
    void reportsProviderTimeoutAsClearAdminError() throws IOException {
        startServer(exchange -> {
            try {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0);
                TimeUnit.MILLISECONDS.sleep(1500);
                exchange.getResponseBody().write("""
                        {"choices":[{"message":{"content":"late response"}}]}
                        """.getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                exchange.close();
            }
        });

        assertThatThrownBy(() -> client(1).complete("system", "user", false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getReason())
                        .contains("request timed out"));
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", handler);
        server.start();
    }

    private LlmClient client(int timeoutSeconds) {
        LlmProperties properties = new LlmProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-model",
                "test-placeholder",
                timeoutSeconds);
        return new GroqLlmClient(properties);
    }

    private static void respond(HttpExchange exchange, int status, String content) throws IOException {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
