package com.smartgym.membership.assistant;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requests.incrementAndGet();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if ("Bearer test-placeholder".equals(authorization)
                    && requestBody.contains("\"response_format\":{\"type\":\"json_object\"}")) {
                jsonMode.incrementAndGet();
            }
            byte[] body;
            if (requests.get() == 1) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(429, body.length);
            } else {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                body = """
                        {"choices":[{"message":{"content":"{\\"subject\\":\\"Renewal\\",\\"body\\":\\"Text\\"}"}}]}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
            }
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        LlmProperties properties = new LlmProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-model", "test-placeholder", 5);

        String content = new GroqLlmClient(properties).complete("system", "user", true);

        assertThat(content).contains("\"subject\":\"Renewal\"");
        assertThat(requests.get()).isEqualTo(2);
        assertThat(jsonMode.get()).isEqualTo(2);
    }
}
