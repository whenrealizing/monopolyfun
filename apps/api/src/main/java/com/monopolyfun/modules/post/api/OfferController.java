package com.monopolyfun.modules.post.api;

import com.monopolyfun.modules.post.api.request.ClosePostRequest;
import com.monopolyfun.modules.post.api.request.PublishOfferRequest;
import com.monopolyfun.modules.post.api.request.UpdateOfferPostRequest;
import com.monopolyfun.modules.post.api.response.OfferCreateResponse;
import com.monopolyfun.modules.post.service.command.PostCommandService;
import com.monopolyfun.modules.post.service.query.PostQueryService;
import com.monopolyfun.modules.post.service.view.OfferView;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/offers")
public class OfferController {
    private final PostQueryService postQueryService;
    private final PostCommandService postCommandService;

    public OfferController(
            PostQueryService postQueryService,
            PostCommandService postCommandService) {
        this.postQueryService = postQueryService;
        this.postCommandService = postCommandService;
    }

    @GetMapping
    public PageResult<OfferView> listOffers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postQueryService.listOffers(status, q, sort, PageQuery.of(limit, cursor), includeAgent);
    }

    @GetMapping("/{offerNo}")
    public OfferView getOffer(
            @PathVariable String offerNo,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postQueryService.getOffer(offerNo, includeAgent);
    }

    @PostMapping
    public OfferCreateResponse publishOffer(
            @Valid @RequestBody PublishOfferRequest request,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postCommandService.createOffer(request, includeAgent);
    }

    @PatchMapping("/{offerNo}")
    public OfferView updateOffer(@PathVariable String offerNo, @Valid @RequestBody UpdateOfferPostRequest request) {
        return postCommandService.updateOffer(offerNo, request);
    }

    @PostMapping("/{offerNo}/close")
    public OfferView closeOffer(@PathVariable String offerNo, @Valid @RequestBody ClosePostRequest request) {
        return postCommandService.closeOffer(offerNo, request);
    }
}
