package com.io.appioweb.adapters.web.aiagents.kanban;

import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class OpenAiKanbanMoveLlmClient implements AiKanbanMoveLlmClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public LlmResponse classify(LlmRequest request) {
        String apiKey = safeTrim(request.apiKey());
        if (apiKey.isBlank()) {
            throw new BusinessException("KANBAN_LLM_API_KEY_MISSING", "Chave da OpenAI ausente para classificador Kanban");
        }

        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("model", safeTrim(request.model()).isBlank() ? "gpt-5-mini" : safeTrim(request.model()));
        payload.put("temperature", 0);
        payload.put("top_p", 1);
        payload.put("max_output_tokens", Math.max(80, request.maxOutputTokens()));
        ArrayNode input = OBJECT_MAPPER.createArrayNode();
        input.add(inputText("system", request.systemPrompt()));
        input.add(inputText("user", request.userPrompt()));
        payload.set("input", input);

        String body;
        try {
            body = OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException("KANBAN_LLM_PAYLOAD_ERROR", "Falha ao serializar payload de classificacao do Kanban");
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw new BusinessException("KANBAN_LLM_CONNECTION_ERROR", "Falha de conexao com a OpenAI no classificador Kanban");
        }

        String responseBody = response.body() == null ? "{}" : response.body();
        if (response.statusCode() >= 400) {
            throw new BusinessException(
                    "KANBAN_LLM_UPSTREAM_ERROR",
                    "OpenAI retornou erro no classificador Kanban (HTTP " + response.statusCode() + "): " + extractErrorDetail(responseBody)
            );
        }

        JsonNode root = parseJson(responseBody);
        String outputText = extractOutputText(root);
        if (outputText.isBlank()) {
            throw new BusinessException("KANBAN_LLM_EMPTY_OUTPUT", "OpenAI nao retornou JSON do classificador Kanban");
        }

        String requestId = safeTrim(root.path("id").asText(""));
        int inputTokens = root.path("usage").path("input_tokens").asInt(0);
        int outputTokens = root.path("usage").path("output_tokens").asInt(0);
        return new LlmResponse(requestId, outputText, Math.max(0, inputTokens), Math.max(0, outputTokens));
    }

    private ObjectNode inputText(String role, String text) {
        ObjectNode message = OBJECT_MAPPER.createObjectNode();
        message.put("role", role);
        ArrayNode content = OBJECT_MAPPER.createArrayNode();
        ObjectNode textNode = OBJECT_MAPPER.createObjectNode();
        textNode.put("type", "input_text");
        textNode.put("text", safeTrim(text));
        content.add(textNode);
        message.set("content", content);
        return message;
    }

    private JsonNode parseJson(String raw) {
        try {
            return OBJECT_MAPPER.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (Exception ex) {
            throw new BusinessException("KANBAN_LLM_INVALID_RESPONSE", "Resposta invalida da OpenAI no classificador Kanban");
        }
    }

    private String extractOutputText(JsonNode response) {
        String direct = safeTrim(response.path("output_text").asText(""));
        if (!direct.isBlank()) return direct;

        JsonNode output = response.path("output");
        if (!output.isArray()) return "";
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) continue;
            for (JsonNode part : content) {
                String text = safeTrim(part.path("text").asText(""));
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    private String extractErrorDetail(String body) {
        JsonNode root = parseJson(body);
        String message = safeTrim(root.path("error").path("message").asText(""));
        if (!message.isBlank()) return message;
        String code = safeTrim(root.path("error").path("code").asText(""));
        if (!code.isBlank()) return code;
        String raw = safeTrim(body);
        if (raw.isBlank()) return "sem detalhes";
        return raw.length() > 180 ? raw.substring(0, 180) : raw;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
