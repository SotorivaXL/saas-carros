package com.io.appioweb.adapters.web.aiagents.response;

import java.util.List;

public record OpenAiModelCatalogHttpResponse(
        List<String> modelVersions,
        List<String> reasoningModels
) {
}
