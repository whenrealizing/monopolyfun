package com.monopolyfun.modules.post.service.view;

import java.util.Map;

public record PublishDraftView(
        String kind,
        Map<String, Object> body
) {
    public PublishDraftView {
        body = body == null ? Map.of() : Map.copyOf(body);
    }
}
