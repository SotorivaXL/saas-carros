package com.io.appioweb.adapters.web.aiagents;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentCompanyStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentCompanyStateEntity;
import com.io.appioweb.adapters.security.SensitiveDataCrypto;
import com.io.appioweb.adapters.web.aiagents.request.AiAgentStateHttpRequest;
import com.io.appioweb.adapters.web.aiagents.response.AiAgentKnowledgeUploadHttpResponse;
import com.io.appioweb.adapters.web.aiagents.response.AiAgentStateHttpResponse;
import com.io.appioweb.adapters.web.aiagents.response.OpenAiModelCatalogHttpResponse;
import com.io.appioweb.application.auth.port.out.CurrentUserPort;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
public class AiAgentController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_CATALOG_MODELS = 20;
    private static final List<String> ALLOWED_MODEL_PREFIXES = List.of(
            "gpt-4",
            "gpt-4o",
            "gpt-4o-mini",
            "gpt-4.1",
            "gpt-4.1-mini",
            "gpt-4.1-nano",
            "gpt-5",
            "gpt-5-mini",
            "gpt-5-nano"
    );
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final CurrentUserPort currentUser;
    private final AiAgentCompanyStateRepositoryJpa aiAgentState;
    private final SensitiveDataCrypto crypto;
    private final String openAiApiKey;

    public AiAgentController(
            CurrentUserPort currentUser,
            AiAgentCompanyStateRepositoryJpa aiAgentState,
            SensitiveDataCrypto crypto,
            @Value("${OPENAI_API_KEY:}") String openAiApiKey
    ) {
        this.currentUser = currentUser;
        this.aiAgentState = aiAgentState;
        this.crypto = crypto;
        this.openAiApiKey = openAiApiKey == null ? "" : openAiApiKey.trim();
    }

    @GetMapping("/ai-agents/state")
    public ResponseEntity<AiAgentStateHttpResponse> getState() {
        UUID companyId = currentUser.companyId();
        var entity = aiAgentState.findById(companyId).orElseGet(() -> defaultEntity(companyId));
        return ResponseEntity.ok(new AiAgentStateHttpResponse(
                parseJson(crypto.decrypt(entity.getProvidersJson()), "[]"),
                parseJson(entity.getAgentsJson(), "[]"),
                parseJson(entity.getKnowledgeBaseJson(), "[]")
        ));
    }

    @PutMapping("/ai-agents/state")
    public ResponseEntity<AiAgentStateHttpResponse> saveState(@RequestBody AiAgentStateHttpRequest req) {
        UUID companyId = currentUser.companyId();
        Instant now = Instant.now();
        var entity = aiAgentState.findById(companyId).orElseGet(() -> {
            var created = defaultEntity(companyId);
            created.setCreatedAt(now);
            return created;
        });

        entity.setProvidersJson(crypto.encrypt(toJson(req.providers(), "[]")));
        entity.setAgentsJson(toJson(req.agents(), "[]"));
        entity.setKnowledgeBaseJson(toJson(req.knowledgeBase(), "[]"));
        entity.setUpdatedAt(now);
        aiAgentState.saveAndFlush(entity);

        return ResponseEntity.ok(new AiAgentStateHttpResponse(
                parseJson(crypto.decrypt(entity.getProvidersJson()), "[]"),
                parseJson(entity.getAgentsJson(), "[]"),
                parseJson(entity.getKnowledgeBaseJson(), "[]")
        ));
    }

    @PostMapping("/ai-agents/knowledge/upload")
    public ResponseEntity<AiAgentKnowledgeUploadHttpResponse> uploadKnowledgeFiles(
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestParam(value = "knowledgeBaseName", required = false) String knowledgeBaseName,
            @RequestParam("files") List<MultipartFile> files
    ) {
        String resolvedKnowledgeBaseId = knowledgeBaseId == null ? "" : knowledgeBaseId.trim();
        if (resolvedKnowledgeBaseId.isBlank()) {
            throw new BusinessException("AI_KNOWLEDGE_BASE_REQUIRED", "knowledgeBaseId e obrigatorio");
        }
        if (files == null || files.isEmpty()) {
            throw new BusinessException("AI_KNOWLEDGE_FILES_REQUIRED", "Envie ao menos 1 arquivo");
        }

        UUID companyId = currentUser.companyId();
        Instant now = Instant.now();
        var entity = aiAgentState.findById(companyId).orElseGet(() -> {
            var created = defaultEntity(companyId);
            created.setCreatedAt(now);
            return created;
        });
        String apiKey = resolveOpenAiApiKey(entity);
        if (apiKey.isBlank()) {
            throw new BusinessException("OPENAI_API_KEY_MISSING", "Configure OPENAI_API_KEY ou apiKey no provedor OpenAI");
        }

        JsonNode knowledgeBaseNode = parseJson(entity.getKnowledgeBaseJson(), "[]");
        if (!knowledgeBaseNode.isArray()) {
            knowledgeBaseNode = OBJECT_MAPPER.createArrayNode();
        }
        ArrayNode knowledgeBaseArray = (ArrayNode) knowledgeBaseNode;
        ObjectNode selectedBase = null;
        for (JsonNode base : knowledgeBaseArray) {
            String baseId = base.path("id").asText("").trim();
            if (!resolvedKnowledgeBaseId.equals(baseId)) continue;
            if (base.isObject()) selectedBase = (ObjectNode) base;
            break;
        }
        if (selectedBase == null) {
            selectedBase = OBJECT_MAPPER.createObjectNode();
            selectedBase.put("id", resolvedKnowledgeBaseId);
            selectedBase.put("name", (knowledgeBaseName == null || knowledgeBaseName.isBlank()) ? "Base principal" : knowledgeBaseName.trim());
            selectedBase.set("vectorStoreIds", OBJECT_MAPPER.createArrayNode());
            selectedBase.set("files", OBJECT_MAPPER.createArrayNode());
            selectedBase.put("createdAt", now.toString());
            selectedBase.put("updatedAt", now.toString());
            knowledgeBaseArray.add(selectedBase);
        }

        List<String> vectorStoreIds = extractVectorStoreIds(selectedBase);
        String vectorStoreId = vectorStoreIds.isEmpty() ? "" : vectorStoreIds.get(0);
        if (vectorStoreId.isBlank()) {
            String storeName = buildVectorStoreName(selectedBase, knowledgeBaseName);
            vectorStoreId = createOpenAiVectorStore(apiKey, storeName);
            vectorStoreIds.add(vectorStoreId);
            selectedBase.put("vectorStoreId", vectorStoreId); // compat legado
        }
        selectedBase.set("vectorStoreIds", toArrayNode(vectorStoreIds));
        selectedBase.put("updatedAt", now.toString());

        List<AiAgentKnowledgeUploadHttpResponse.UploadedFileHttpResponse> uploadedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            String fileName = safeFileName(file == null ? "" : file.getOriginalFilename());
            if (file == null || file.isEmpty()) continue;
            String openAiFileId = uploadFileToOpenAi(apiKey, fileName, file);
            String attachStatus = attachFileToVectorStore(apiKey, vectorStoreId, openAiFileId);
            uploadedFiles.add(new AiAgentKnowledgeUploadHttpResponse.UploadedFileHttpResponse(fileName, openAiFileId, vectorStoreId, attachStatus));
        }
        if (uploadedFiles.isEmpty()) {
            throw new BusinessException("AI_KNOWLEDGE_FILES_EMPTY", "Nenhum arquivo valido foi enviado");
        }

        entity.setKnowledgeBaseJson(toJson(knowledgeBaseArray, "[]"));
        entity.setUpdatedAt(now);
        aiAgentState.saveAndFlush(entity);

        return ResponseEntity.ok(new AiAgentKnowledgeUploadHttpResponse(
                resolvedKnowledgeBaseId,
                vectorStoreIds,
                uploadedFiles
        ));
    }

    @GetMapping("/ai-agents/openai-models")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<OpenAiModelCatalogHttpResponse> listOpenAiModels() {
        UUID companyId = currentUser.companyId();
        var entity = aiAgentState.findById(companyId).orElseGet(() -> defaultEntity(companyId));
        String apiKey = resolveOpenAiApiKey(entity);
        if (apiKey.isBlank()) {
            throw new BusinessException("OPENAI_API_KEY_MISSING", "Configure a chave da OpenAI no provedor ou na variavel OPENAI_API_KEY");
        }

        String body = fetchOpenAiModels(apiKey);
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(body);
        } catch (Exception ex) {
            throw new BusinessException("OPENAI_MODELS_PARSE_ERROR", "Nao foi possivel interpretar a resposta da OpenAI");
        }

        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new BusinessException("OPENAI_MODELS_INVALID_RESPONSE", "Resposta inesperada da OpenAI ao listar modelos");
        }

        List<String> allModels = new ArrayList<>();
        for (JsonNode item : data) {
            String id = item.path("id").asText("").trim();
            if (id.isEmpty()) continue;
            if (id.startsWith("ft:")) continue;
            if (!isAllowedModelForCatalog(id)) continue;
            allModels.add(id);
        }

        allModels = allModels.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .limit(MAX_CATALOG_MODELS)
                .toList();

        List<String> reasoningOnly = allModels.stream()
                .filter(this::isReasoningModel)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<String> reasoningModels = reasoningOnly.isEmpty() ? allModels : reasoningOnly;

        return ResponseEntity.ok(new OpenAiModelCatalogHttpResponse(allModels, reasoningModels));
    }

    private JpaAiAgentCompanyStateEntity defaultEntity(UUID companyId) {
        Instant now = Instant.now();
        JpaAiAgentCompanyStateEntity entity = new JpaAiAgentCompanyStateEntity();
        entity.setCompanyId(companyId);
        entity.setProvidersJson(crypto.encrypt("[]"));
        entity.setAgentsJson("[]");
        entity.setKnowledgeBaseJson("[]");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private static JsonNode parseJson(String value, String fallbackJson) {
        try {
            return OBJECT_MAPPER.readTree(value == null || value.isBlank() ? fallbackJson : value);
        } catch (Exception ignored) {
            try {
                return OBJECT_MAPPER.readTree(fallbackJson);
            } catch (Exception impossible) {
                return OBJECT_MAPPER.createArrayNode();
            }
        }
    }

    private static String toJson(JsonNode value, String fallbackJson) {
        if (value == null || value.isNull()) return fallbackJson;
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ignored) {
            return fallbackJson;
        }
    }

    private String resolveOpenAiApiKey(JpaAiAgentCompanyStateEntity entity) {
        if (!openAiApiKey.isBlank()) return openAiApiKey;
        try {
            JsonNode providers = parseJson(crypto.decrypt(entity.getProvidersJson()), "[]");
            for (JsonNode provider : providers) {
                String type = provider.path("type").asText("").trim().toLowerCase();
                if (!"openai".equals(type)) continue;
                String key = provider.path("apiKey").asText("").trim();
                if (!key.isBlank()) return key;
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private String fetchOpenAiModels(String apiKey) {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/models"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        try {
            HttpResponse<String> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                throw new BusinessException("OPENAI_MODELS_UPSTREAM_ERROR", "OpenAI retornou erro ao listar modelos (HTTP " + res.statusCode() + ")");
            }
            return res.body() == null ? "{}" : res.body();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("OPENAI_MODELS_CONNECTION_ERROR", "Falha de conexao ao consultar modelos da OpenAI");
        }
    }

    private boolean isReasoningModel(String modelId) {
        String normalized = modelId == null ? "" : modelId.trim().toLowerCase();
        if (normalized.isBlank()) return false;
        if (normalized.startsWith("gpt-4")) return true;
        if (normalized.startsWith("gpt-4o")) return true;
        if (normalized.startsWith("gpt-4.1")) return true;
        if (normalized.startsWith("o1")) return true;
        if (normalized.startsWith("o3")) return true;
        if (normalized.startsWith("o4")) return true;
        if (normalized.startsWith("o5")) return true;
        if (normalized.startsWith("gpt-5")) return true;
        return normalized.contains("reasoning");
    }

    private boolean isAllowedModelForCatalog(String modelId) {
        String normalized = modelId == null ? "" : modelId.trim().toLowerCase();
        if (normalized.isBlank()) return false;
        if (!isStableModel(normalized)) return false;
        for (String prefix : ALLOWED_MODEL_PREFIXES) {
            if (normalized.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isStableModel(String normalizedModelId) {
        if (normalizedModelId.contains("preview")) return false;
        if (normalizedModelId.contains("beta")) return false;
        if (normalizedModelId.contains("alpha")) return false;
        if (normalizedModelId.contains("rc")) return false;
        if (normalizedModelId.contains("experimental")) return false;
        if (normalizedModelId.endsWith("-exp")) return false;
        if (normalizedModelId.matches(".*-\\d{4}-\\d{2}-\\d{2}$")) return false;
        if (normalizedModelId.matches(".*-\\d{4}-\\d{2}$")) return false;
        if (normalizedModelId.matches(".*-\\d{8}$")) return false;
        return true;
    }

    private List<String> extractVectorStoreIds(JsonNode base) {
        List<String> ids = new ArrayList<>();
        String single = base.path("vectorStoreId").asText("").trim();
        if (!single.isBlank()) ids.add(single);
        JsonNode many = base.path("vectorStoreIds");
        if (many.isArray()) {
            for (JsonNode node : many) {
                String id = node.asText("").trim();
                if (id.isBlank() || ids.contains(id)) continue;
                ids.add(id);
            }
        }
        return ids;
    }

    private ArrayNode toArrayNode(List<String> values) {
        ArrayNode node = OBJECT_MAPPER.createArrayNode();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            node.add(value.trim());
        }
        return node;
    }

    private String buildVectorStoreName(JsonNode selectedBase, String knowledgeBaseName) {
        String explicit = knowledgeBaseName == null ? "" : knowledgeBaseName.trim();
        if (!explicit.isBlank()) return explicit;
        String baseName = selectedBase.path("name").asText("").trim();
        return baseName.isBlank() ? "knowledge_base" : baseName;
    }

    private String createOpenAiVectorStore(String apiKey, String name) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("name", name == null || name.isBlank() ? "knowledge_base" : name.trim());
        String body;
        try {
            body = OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException("OPENAI_VECTOR_STORE_PAYLOAD_ERROR", "Falha ao montar payload de vector store");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/vector_stores"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(35))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode response = sendOpenAiRequest(req, "OPENAI_VECTOR_STORE_CREATE_ERROR", "Falha ao criar vector store");
        String vectorStoreId = response.path("id").asText("").trim();
        if (vectorStoreId.isBlank()) {
            throw new BusinessException("OPENAI_VECTOR_STORE_CREATE_ERROR", "OpenAI nao retornou id da vector store");
        }
        return vectorStoreId;
    }

    private String uploadFileToOpenAi(String apiKey, String fileName, MultipartFile file) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (Exception ex) {
            throw new BusinessException("OPENAI_FILE_READ_ERROR", "Falha ao ler arquivo para upload");
        }
        if (data.length == 0) {
            throw new BusinessException("OPENAI_FILE_EMPTY", "Arquivo vazio nao pode ser enviado");
        }
        String boundary = "----AppIoWebBoundary" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, fileName, file.getContentType(), data);
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/files"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        JsonNode response = sendOpenAiRequest(req, "OPENAI_FILE_UPLOAD_ERROR", "Falha ao enviar arquivo para OpenAI");
        String openAiFileId = response.path("id").asText("").trim();
        if (openAiFileId.isBlank()) {
            throw new BusinessException("OPENAI_FILE_UPLOAD_ERROR", "OpenAI nao retornou file_id");
        }
        return openAiFileId;
    }

    private String attachFileToVectorStore(String apiKey, String vectorStoreId, String fileId) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("file_id", fileId);
        String body;
        try {
            body = OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException("OPENAI_VECTOR_STORE_ATTACH_ERROR", "Falha ao montar payload de indexacao");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/vector_stores/" + vectorStoreId + "/files"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(35))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode response = sendOpenAiRequest(req, "OPENAI_VECTOR_STORE_ATTACH_ERROR", "Falha ao indexar arquivo no vector store");
        String status = response.path("status").asText("").trim();
        return status.isBlank() ? "in_progress" : status;
    }

    private JsonNode sendOpenAiRequest(HttpRequest req, String errorCode, String errorMessage) {
        HttpResponse<String> res;
        try {
            res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw new BusinessException(errorCode, errorMessage + ": erro de conexao");
        }
        String responseBody = res.body() == null ? "{}" : res.body();
        if (res.statusCode() >= 400) {
            throw new BusinessException(errorCode, errorMessage + " (HTTP " + res.statusCode() + "): " + extractOpenAiErrorDetail(responseBody));
        }
        try {
            return OBJECT_MAPPER.readTree(responseBody);
        } catch (Exception ex) {
            throw new BusinessException(errorCode, errorMessage + ": resposta invalida");
        }
    }

    private String extractOpenAiErrorDetail(String body) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(body == null ? "{}" : body);
            String message = node.path("error").path("message").asText("").trim();
            if (!message.isBlank()) return message;
            return body == null ? "" : body;
        } catch (Exception ignored) {
            return body == null ? "" : body;
        }
    }

    private String safeFileName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return "arquivo.bin";
        return value.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    private byte[] buildMultipartBody(String boundary, String fileName, String contentType, byte[] fileContent) {
        String type = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Disposition: form-data; name=\"purpose\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write("assistants\r\n".getBytes(StandardCharsets.UTF_8));

            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(fileContent);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException("OPENAI_FILE_UPLOAD_ERROR", "Falha ao montar multipart do arquivo");
        }
    }
}
