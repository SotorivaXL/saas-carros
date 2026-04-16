package com.io.appioweb.adapters.web.crm.response;

import tools.jackson.databind.JsonNode;

public record CrmStateHttpResponse(
        JsonNode stages,
        JsonNode leadStageMap,
        JsonNode customFields,
        JsonNode leadFieldValues,
        JsonNode leadFieldOrder
) {
}
