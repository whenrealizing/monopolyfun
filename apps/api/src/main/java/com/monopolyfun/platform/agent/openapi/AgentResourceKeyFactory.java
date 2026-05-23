package com.monopolyfun.platform.agent.openapi;

import org.springframework.stereotype.Component;

@Component
public class AgentResourceKeyFactory {
    public String offer(String offerNo) {
        return key("post.offer", offerNo);
    }

    public String request(String requestNo) {
        return key("post.request", requestNo);
    }

    public String project(String projectNo) {
        return key("project", projectNo);
    }

    public String postItem(String itemId) {
        return key("post.item", itemId);
    }

    public String order(String orderNo) {
        return key("order", orderNo);
    }

    public String paymentIntent(String intentId) {
        return key("payment.intent", intentId);
    }

    public String shareReleaseRequest(String requestId) {
        return key("share.release_request", requestId);
    }

    private String key(String type, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return type + ":" + id;
    }
}
