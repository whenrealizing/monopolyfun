package com.monopolyfun.modules.post.api.response;

import com.monopolyfun.modules.post.service.view.RequestView;

public record RequestCreateResponse(
        RequestView request
) {
}
