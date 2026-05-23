package com.monopolyfun.modules.identity.api;

import com.monopolyfun.modules.identity.api.request.CompleteIdentityVerificationRequest;
import com.monopolyfun.modules.identity.api.request.StartIdentityVerificationRequest;
import com.monopolyfun.modules.identity.api.request.UpdateIdentityDisplaySkinRequest;
import com.monopolyfun.modules.identity.api.request.UpdateIdentityProfileRequest;
import com.monopolyfun.modules.identity.service.command.IdentityProfileCommandService;
import com.monopolyfun.modules.identity.service.display.IdentityDisplaySkinService;
import com.monopolyfun.modules.identity.service.query.IdentityQueryService;
import com.monopolyfun.modules.identity.service.verification.IdentityVerificationService;
import com.monopolyfun.modules.identity.service.view.IdentityCertifierView;
import com.monopolyfun.modules.identity.service.view.IdentityPageView;
import com.monopolyfun.modules.identity.service.view.IdentityVerificationStartResponse;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/identity")
public class IdentityController {
    private final IdentityQueryService identityQueryService;
    private final IdentityProfileCommandService identityProfileCommandService;
    private final IdentityVerificationService identityVerificationService;
    private final IdentityDisplaySkinService identityDisplaySkinService;
    private final CurrentAccountAccess currentAccountAccess;

    public IdentityController(
            IdentityQueryService identityQueryService,
            IdentityProfileCommandService identityProfileCommandService,
            IdentityVerificationService identityVerificationService,
            IdentityDisplaySkinService identityDisplaySkinService,
            CurrentAccountAccess currentAccountAccess) {
        this.identityQueryService = identityQueryService;
        this.identityProfileCommandService = identityProfileCommandService;
        this.identityVerificationService = identityVerificationService;
        this.identityDisplaySkinService = identityDisplaySkinService;
        this.currentAccountAccess = currentAccountAccess;
    }

    @GetMapping
    public IdentityPageView getCurrentIdentity() {
        return identityQueryService.getCurrentIdentity();
    }

    @PatchMapping("/profile")
    public IdentityPageView updateProfile(@Valid @RequestBody UpdateIdentityProfileRequest request) {
        identityProfileCommandService.updateProfile(currentAccountAccess.requireAccountId(), request);
        return identityQueryService.getCurrentIdentity();
    }

    @GetMapping("/certifiers")
    public List<IdentityCertifierView> listCertifiers() {
        return identityVerificationService.listCertifiers().stream().map(com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper::identityCertifier).toList();
    }

    @PostMapping("/verifications/start")
    public IdentityVerificationStartResponse startVerification(@Valid @RequestBody StartIdentityVerificationRequest request) {
        return identityVerificationService.beginVerification(
                currentAccountAccess.requireAccountId(),
                request.certifierId(),
                request.input());
    }

    @PostMapping("/verifications/{challengeId}/complete")
    public void completeVerification(
            @PathVariable String challengeId,
            @RequestBody(required = false) CompleteIdentityVerificationRequest request) {
        identityVerificationService.completeVerification(
                currentAccountAccess.requireAccountId(),
                challengeId,
                request == null ? null : request.input());
    }

    @PostMapping("/display-skin")
    public IdentityPageView updateDisplaySkin(@Valid @RequestBody UpdateIdentityDisplaySkinRequest request) {
        return identityDisplaySkinService.update(
                currentAccountAccess.requireAccountId(),
                request.source(),
                request.certifierId());
    }

    @GetMapping("/oauth/github/callback")
    public ResponseEntity<Void> githubVerificationCallback(
            @RequestParam String code,
            @RequestParam String state) {
        var challenge = identityQueryService.findChallengeByToken(state);
        identityVerificationService.completeVerification(challenge.accountId(), challenge.id(), java.util.Map.of("code", code, "state", state));
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, URI.create("/identity").toString())
                .build();
    }
}
