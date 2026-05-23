package com.monopolyfun.modules.post.api;

import com.monopolyfun.modules.post.service.query.PublicFeedQueryService;
import com.monopolyfun.modules.post.service.view.PublicFeedView;
import com.monopolyfun.shared.pagination.PageQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicFeedController {
    private final PublicFeedQueryService publicFeedQueryService;

    public PublicFeedController(PublicFeedQueryService publicFeedQueryService) {
        this.publicFeedQueryService = publicFeedQueryService;
    }

    @GetMapping("/home-feed")
    public PublicFeedView getHomeFeed() {
        return publicFeedQueryService.homeFeed();
    }

    @GetMapping("/market-feed")
    public PublicFeedView getMarketFeed(
            @RequestParam(defaultValue = "all") String kind,
            @RequestParam(defaultValue = "open") String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "24") int limit,
            @RequestParam(required = false) String cursor) {
        return publicFeedQueryService.marketFeed(kind, status, q, sort, PageQuery.of(limit, cursor));
    }
}
