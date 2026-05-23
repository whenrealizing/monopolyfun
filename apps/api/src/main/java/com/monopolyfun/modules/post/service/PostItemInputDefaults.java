package com.monopolyfun.modules.post.service;

import java.util.List;
import java.util.Locale;

public final class PostItemInputDefaults {
    public static final String DEFAULT_MODE = PostItemSupport.DELIVERY_MODE_REVIEWED;
    public static final int DEFAULT_QUANTITY = 1;
    public static final List<String> DEFAULT_ACCEPTANCE_CRITERIA = List.of("evidence");

    private PostItemInputDefaults() {
    }

    public static String buyerNotePlaceholder(String itemKind) {
        return switch (itemKind == null ? "work" : itemKind.toLowerCase(Locale.ROOT)) {
            case "product" -> "有特殊要求可以备注，默认购买 1 份。";
            case "service", "work" -> "请描述需求、参考链接和期望格式。";
            default -> "请补充交付所需的关键说明。";
        };
    }
}
