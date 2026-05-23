package com.monopolyfun.modules.post.api;

import com.monopolyfun.modules.post.api.request.ClosePostRequest;
import com.monopolyfun.modules.post.api.request.PublishRequestRequest;
import com.monopolyfun.modules.post.api.request.UpdateRequestPostRequest;
import com.monopolyfun.modules.post.api.response.RequestCreateResponse;
import com.monopolyfun.modules.post.service.command.PostCommandService;
import com.monopolyfun.modules.post.service.query.PostQueryService;
import com.monopolyfun.modules.post.service.view.RequestView;
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
@RequestMapping("/api/v1/requests")
public class RequestController {
    private final PostQueryService postQueryService;
    private final PostCommandService postCommandService;

    public RequestController(
            PostQueryService postQueryService,
            PostCommandService postCommandService) {
        this.postQueryService = postQueryService;
        this.postCommandService = postCommandService;
    }

    @GetMapping
    public PageResult<RequestView> listRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postQueryService.listRequests(status, q, sort, PageQuery.of(limit, cursor), includeAgent);
    }

    @GetMapping("/{requestNo}")
    public RequestView getRequest(
            @PathVariable String requestNo,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postQueryService.getRequest(requestNo, includeAgent);
    }

    @PostMapping
    public RequestCreateResponse publishRequest(
            @Valid @RequestBody PublishRequestRequest request,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postCommandService.createRequest(request, includeAgent);
    }

    @PatchMapping("/{requestNo}")
    public RequestView updateRequest(@PathVariable String requestNo, @Valid @RequestBody UpdateRequestPostRequest request) {
        return postCommandService.updateRequest(requestNo, request);
    }

    @PostMapping("/{requestNo}/close")
    public RequestView closeRequest(@PathVariable String requestNo, @Valid @RequestBody ClosePostRequest request) {
        return postCommandService.closeRequest(requestNo, request);
    }
}
