package com.io.appioweb.adapters.web.crm.request;

import tools.jackson.databind.JsonNode;

public record CrmStateHttpRequest(
        JsonNode stages,
        JsonNode leadStageMap,
        JsonNode customFields,
        JsonNode leadFieldValues,
        JsonNode leadFieldOrder
) {
}
