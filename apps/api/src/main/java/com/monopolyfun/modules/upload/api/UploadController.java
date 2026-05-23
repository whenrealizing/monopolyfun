package com.monopolyfun.modules.upload.api;

import com.monopolyfun.modules.payment.api.request.PaymentActionRequest;
import com.monopolyfun.modules.upload.api.request.CompleteUploadRequest;
import com.monopolyfun.modules.upload.api.request.UploadPresignRequest;
import com.monopolyfun.modules.upload.api.response.UploadCompletionResponse;
import com.monopolyfun.modules.upload.api.response.UploadDownloadResponse;
import com.monopolyfun.modules.upload.api.response.UploadPresignResponse;
import com.monopolyfun.modules.upload.service.UploadService;
import com.monopolyfun.modules.upload.service.view.ProofAssetView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {
    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/presign")
    public UploadPresignResponse presign(@Valid @RequestBody UploadPresignRequest request) {
        return uploadService.presign(request);
    }

    @PostMapping("/{assetId}/complete")
    public UploadCompletionResponse complete(
            @PathVariable String assetId,
            @Valid @RequestBody CompleteUploadRequest request) {
        return uploadService.complete(assetId, request);
    }

    @PostMapping("/{assetId}/download")
    public UploadDownloadResponse download(@PathVariable String assetId) {
        return uploadService.download(assetId);
    }

    @PostMapping("/{assetId}/verify")
    public ProofAssetView verify(@PathVariable String assetId, @Valid @RequestBody PaymentActionRequest request) {
        return uploadService.verify(assetId, request);
    }

    @PostMapping("/{assetId}/quarantine")
    public ProofAssetView quarantine(@PathVariable String assetId, @Valid @RequestBody PaymentActionRequest request) {
        return uploadService.quarantine(assetId, request);
    }

    @PostMapping("/{assetId}/cancel")
    public ProofAssetView cancel(@PathVariable String assetId, @Valid @RequestBody PaymentActionRequest request) {
        return uploadService.cancel(assetId, request);
    }
}
