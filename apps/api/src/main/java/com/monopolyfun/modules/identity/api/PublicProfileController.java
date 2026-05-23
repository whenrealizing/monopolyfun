package com.monopolyfun.modules.identity.api;

import com.monopolyfun.modules.identity.service.query.PublicProfileQueryService;
import com.monopolyfun.modules.identity.service.view.PublicProfileView;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicProfileController {
    private final PublicProfileQueryService publicProfileQueryService;

    public PublicProfileController(PublicProfileQueryService publicProfileQueryService) {
        this.publicProfileQueryService = publicProfileQueryService;
    }

    @GetMapping("/profiles/{handle}")
    @Operation(operationId = "getPublicProfile")
    public PublicProfileView getPublicProfile(@PathVariable String handle) {
        return publicProfileQueryService.getByHandle(handle);
    }
}
