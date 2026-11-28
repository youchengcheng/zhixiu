package com.ycc.zhixiucloud.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class ConciergeRequest {

    private ConciergeRequest() {
    }

    public record CreateSession(@Size(max = 80) String title) {
    }

    public record Chat(@NotNull UUID requestId, @NotBlank @Size(max = 1000) String content, @Positive Long orderId) {
    }
}
