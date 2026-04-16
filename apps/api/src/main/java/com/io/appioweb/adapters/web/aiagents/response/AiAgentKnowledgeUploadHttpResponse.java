package com.io.appioweb.adapters.web.aiagents.response;

import java.util.List;

public record AiAgentKnowledgeUploadHttpResponse(
        String knowledgeBaseId,
        List<String> vectorStoreIds,
        List<UploadedFileHttpResponse> uploadedFiles
) {
    public record UploadedFileHttpResponse(
            String fileName,
            String openAiFileId,
            String vectorStoreId,
            String status
    ) {}
}
