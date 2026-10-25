package com.smartgym.membership.assistant;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GroqLlmClient implements LlmClient {
    private final RestClient restClient;
    private final LlmProperties properties;

    public GroqLlmClient(LlmProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, boolean jsonResponse) {
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        if (jsonResponse) {
            body = new java.util.HashMap<>(body);
            body.put("response_format", Map.of("type", "json_object"));
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                ChatCompletion response = restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + properties.apiKey())
                        .body(body)
                        .retrieve()
                        .body(ChatCompletion.class);
                if (response == null || response.choices() == null || response.choices().isEmpty()
                        || response.choices().get(0).message() == null
                        || response.choices().get(0).message().content() == null) {
                    throw new ResponseStatusException(HttpStatusCode.valueOf(502), "LLM returned an empty response.");
                }
                return response.choices().get(0).message().content();
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() == 429 && attempt == 0) {
                    pauseBeforeRetry();
                    continue;
                }
                if (exception.getStatusCode().value() == 429) {
                    throw new ResponseStatusException(
                            HttpStatusCode.valueOf(503), "LLM provider rate limit persisted after one retry.");
                }
                if (exception.getStatusCode().is5xxServerError()) {
                    throw new ResponseStatusException(
                            HttpStatusCode.valueOf(502), "LLM provider is temporarily unavailable.");
                }
                throw new ResponseStatusException(
                        HttpStatusCode.valueOf(502), "LLM provider rejected the request.");
            } catch (ResourceAccessException exception) {
                if (hasTimeout(exception)) {
                    throw new ResponseStatusException(
                            HttpStatusCode.valueOf(504), "LLM provider request timed out.");
                }
                throw new ResponseStatusException(
                        HttpStatusCode.valueOf(502), "LLM provider is unavailable.");
            } catch (RestClientException exception) {
                if (hasTimeout(exception)) {
                    throw new ResponseStatusException(
                            HttpStatusCode.valueOf(504), "LLM provider request timed out.");
                }
                throw new ResponseStatusException(
                        HttpStatusCode.valueOf(502), "LLM provider returned an invalid response.");
            }
        }
        throw new ResponseStatusException(
                HttpStatusCode.valueOf(503), "LLM provider rate limit persisted after one retry.");
    }

    private static void pauseBeforeRetry() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(503), "LLM provider retry was interrupted.");
        }
    }

    private static boolean hasTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletion(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(String content) {}
}
