package com.behcm.domain.admin.knowledgeaccess.dto;

import jakarta.validation.constraints.NotNull;

public record AdminKnowledgeAccessRequest(
        @NotNull Boolean canRead,
        @NotNull Boolean canWrite
) {
}
