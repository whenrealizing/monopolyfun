package com.monopolyfun.modules.post.api;

import com.monopolyfun.modules.order.api.request.CreatePostOrderRequest;
import com.monopolyfun.modules.post.api.request.ClosePostRequest;
import com.monopolyfun.modules.post.api.request.CreatePostItemRequest;
import com.monopolyfun.modules.post.service.command.PostItemCommandService;
import com.monopolyfun.modules.post.service.query.PostItemWorkspaceQueryService;
import com.monopolyfun.modules.post.service.view.PostItemView;
import com.monopolyfun.modules.post.service.view.PostWorkspaceView;
import com.monopolyfun.shared.command.CommandReceipt;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PostItemController {
    private final PostItemWorkspaceQueryService postItemWorkspaceQueryService;
    private final PostItemCommandService postItemCommandService;

    public PostItemController(
            PostItemWorkspaceQueryService postItemWorkspaceQueryService,
            PostItemCommandService postItemCommandService) {
        this.postItemWorkspaceQueryService = postItemWorkspaceQueryService;
        this.postItemCommandService = postItemCommandService;
    }

    @GetMapping("/posts/{postNo}/workspace")
    public PostWorkspaceView getPostWorkspace(
            @PathVariable String postNo,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postItemWorkspaceQueryService.getWorkspace(postNo, includeAgent);
    }

    @GetMapping("/posts/{postNo}/items")
    public List<PostItemView> listPostItems(
            @PathVariable String postNo,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postItemWorkspaceQueryService.listItems(postNo, includeAgent);
    }

    @PostMapping("/posts/{postNo}/items")
    @Operation(summary = "创建报价或需求任务项", description = "Project 任务项请使用 /api/v1/projects/{projectNo}/items，以免 amount、quantity、agentInstruction 语义混入项目合同。")
    public PostItemView createPostItem(
            @PathVariable String postNo,
            @Valid @RequestBody CreatePostItemRequest request,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postItemCommandService.createItem(postNo, request, includeAgent);
    }

    @GetMapping("/items/{itemId}")
    public PostItemView getPostItem(
            @PathVariable String itemId,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postItemWorkspaceQueryService.getItem(itemId, includeAgent);
    }

    @PatchMapping("/items/{itemId}")
    public PostItemView updatePostItem(@PathVariable String itemId, @Valid @RequestBody CreatePostItemRequest request) {
        return postItemCommandService.updateItem(itemId, request);
    }

    @PostMapping("/items/{itemId}/close")
    public PostItemView closePostItem(@PathVariable String itemId, @Valid @RequestBody ClosePostRequest request) {
        return postItemCommandService.closeItem(itemId, request);
    }

    @PostMapping("/items/{itemId}/claim")
    public CommandReceipt claimPostItem(@PathVariable String itemId, @Valid @RequestBody CreatePostOrderRequest request) {
        return postItemCommandService.claimItem(itemId, request.actorAccountId(), request.buyerNote(), request.paymentRecipient(), request.deliveryInput());
    }
}
