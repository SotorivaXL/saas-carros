package com.io.appioweb.adapters.web.aiagents.response;

import java.util.List;

public record AiAgentToolKbSearchHttpResponse(
        List<KbChunkHttpResponse> chunks,
        boolean lowConfidence
) {
    public record KbChunkHttpResponse(
            String source,
            double score,
            String text
    ) {
    }
}
