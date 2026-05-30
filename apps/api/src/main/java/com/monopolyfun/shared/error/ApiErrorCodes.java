package com.monopolyfun.shared.error;

import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public final class ApiErrorCodes {
    public static final String VALIDATION_FAILED = "validation.failed";
    public static final String RESOURCE_NOT_FOUND = "resource.not_found";
    public static final String SERVER_INTERNAL = "server.internal";
    public static final String UNKNOWN = "error.unknown";

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9]+(?:[._][a-z0-9]+)*(?:\\.[a-z0-9]+(?:[._][a-z0-9]+)*)+$");
    // 默认消息作为 Map key，重复文案会导致错误码表初始化失败。
    private static final Map<String, String> EXACT_REASON_CODES = Map.ofEntries(
            entry("Account not found", "account.not_found"),
            entry("Artifact must be registered before proof submission", "order.proof.artifact_registration_required"),
            entry("Artifact upload is not complete", "order.proof.artifact_upload_incomplete"),
            entry("Authentication required", "auth.required"),
            entry("System capability required", "auth.system_capability.required"),
            entry("CSRF token mismatch", "auth.csrf.mismatch"),
            entry("CREATE is only valid for market creation", "market.action.create_invalid"),
            entry("Callback token mismatch", "payment.callback.token_mismatch"),
            entry("Agent summary is too long", "identity.profile.agent_summary.invalid_length"),
            entry("Avatar URL is too long", "identity.profile.avatar_url.invalid_length"),
            entry("Avatar URL must be http or https", "identity.profile.avatar_url.invalid_format"),
            entry("Handle already exists", "auth.handle.taken"),
            entry("Handle must be 3-20 chars of letters, digits, _ or -", "auth.handle.invalid_format"),
            entry("Display name is required", "identity.profile.display_name.required"),
            entry("Display name is too long", "identity.profile.display_name.invalid_length"),
            entry("Display skin certifier is not verified", "identity.display_skin.certifier_unverified"),
            entry("Display skin certifier is required", "identity.display_skin.certifier_required"),
            entry("Display skin source is invalid", "identity.display_skin.source_invalid"),
            entry("Identity verification callback state mismatch", "identity.verification.state_mismatch"),
            entry("Invalid handle or password", "auth.login.invalid_credentials"),
            entry("Invalid reset token", "auth.password_reset.token.invalid"),
            entry("Inventory limit cannot be below active orders", "listing.inventory_limit.below_active_orders"),
            entry("Lead account not found", "market.lead_account.not_found"),
            entry("Lead is already market owner", "market.lead.already_owner"),
            entry("Listing already archived", "listing.already_archived"),
            entry("Listing already closed", "listing.already_closed"),
            entry("Listing is not publishable", "listing.publish.invalid_state"),
            entry("Listing is not reopenable", "listing.reopen.invalid_state"),
            entry("Listing not found", "listing.not_found"),
            entry("Market member already exists", "market.member.already_exists"),
            entry("Market member not found", "market.member.not_found"),
            entry("Market not found", "market.not_found"),
            entry("Member account not found", "market.member_account.not_found"),
            entry("Money listing settlementSpec must include an amount", "listing.money_settlement.amount_required"),
            entry("Money order requires a positive settlement amount", "payment.money_order.amount_required"),
            entry("Money settlement requires a payment intent", "payment.money_settlement.intent_required"),
            entry("Money settlement requires captured payment", "payment.money_settlement.capture_required"),
            entry("New password is required", "auth.password.required"),
            entry("OAuth state expired or already used", "auth.oauth.state.unusable"),
            entry("Only claimed account can submit proof", "order.proof.submitter_not_claimed_account"),
            entry("Only delivered orders can be disputed", "order.dispute.invalid_state"),
            entry("Only disputed orders can assign reviewer", "order.review_assign.invalid_state"),
            entry("Only disputed orders can be overridden", "order.review_override.invalid_state"),
            entry("Order review capability required", "order.accept.forbidden"),
            entry("Only market lead can add members", "market.member_add.forbidden"),
            entry("Order dispute resolve capability required", "order.dispute.resolve.forbidden"),
            entry("Only market lead can change market status", "market.status_change.forbidden"),
            entry("Only market lead can remove members", "market.member_remove.forbidden"),
            entry("Only market lead can update market", "market.update.forbidden"),
            entry("Only market lead or member can open listing", "listing.open.forbidden"),
            entry("Only market lead or member can publish listing", "listing.publish.forbidden"),
            entry("Only market lead or member can update listing", "listing.update.forbidden"),
            entry("Order participant or dispute capability required", "order.dispute.forbidden"),
            entry("Opener account not found", "listing.opener_account.not_found"),
            entry("Order already terminal", "order.already_terminal"),
            entry("Order is not a money settlement", "payment.order.not_money_settlement"),
            entry("Order is not awaiting proof", "order.proof.invalid_state"),
            entry("Order is not delivered", "order.accept.invalid_state"),
            entry("Order not found", "order.not_found"),
            entry("Password login unavailable for this account", "auth.login.password_unavailable"),
            entry("Payment amount mismatch", "payment.amount_mismatch"),
            entry("Payment callback signature invalid", "payment.callback.signature_invalid"),
            entry("Payment actor must match order claimant", "payment.actor.order_claimant_required"),
            entry("Payment intent can only be created for claimed or delivered orders", "payment.intent.invalid_order_state"),
            entry("Payment intent not found", "payment.intent.not_found"),
            entry("Payment transition actor is not allowed", "payment.transition.forbidden"),
            entry("Proof requires at least one link or artifact", "order.proof.evidence_required"),
            entry("Project account already has a concrete role", "project.role.member_redundant"),
            entry("Request actor must match authenticated account", "auth.actor.mismatch"),
            entry("Account banned by risk control", "risk.account.banned"),
            entry("Account frozen by risk control", "risk.account.frozen"),
            entry("Reset token expired or already used", "auth.password_reset.token.unusable"),
            entry("Session cookie required", "auth.session_cookie.required"),
            entry("Too many password reset confirm attempts", "auth.password_reset.confirm_rate_limited"),
            entry("Too many login attempts", "auth.login.rate_limited"),
            entry("Too many OAuth authorize requests", "auth.oauth.authorize_rate_limited"),
            entry("Too many OAuth callback requests", "auth.oauth.callback_rate_limited"),
            entry("Too many registration attempts", "auth.register.rate_limited"),
            entry("Too many password reset requests", "auth.password_reset.rate_limited"),
            entry("Too many payment callback attempts", "payment.callback.rate_limited"),
            entry("Too many upload presign requests", "upload.presign.rate_limited"),
            entry("UPDATE must use /update", "market.action.update_invalid"),
            entry("Unsupported payment callback status", "payment.callback.status.unsupported"),
            entry("Unsupported upload content type", "upload.content_type.unsupported"),
            entry("Upload asset is not pending", "upload.asset.invalid_state"),
            entry("Upload asset not found", "upload.asset.not_found"),
            entry("Upload checksum mismatch", "upload.checksum_mismatch"),
            entry("Upload content length mismatch", "upload.content_length_mismatch"),
            entry("Upload content type mismatch", "upload.content_type_mismatch"),
            entry("Upload exceeds max size", "upload.content_length.exceeded"),
            entry("Upload requires order participant or market lead", "upload.order_access.forbidden"),
            entry("Workbench item cannot be dismissed", "workbench.item.dismiss_forbidden"),
            entry("Workbench item not found", "workbench.item.not_found"),
            entry("backoffice scene currently only supports intent=view", "agent.backoffice.intent.view_only"),
            entry("checksumSha256 must be 64 hex chars", "upload.checksum.invalid_format"),
            entry("home scene only supports intent=view", "agent.home.intent.view_only"),
            entry("identity scene currently only supports intent=view", "agent.identity.intent.view_only"),
            entry("intent is required", "agent.intent.required"),
            entry("scene is required", "agent.scene.required"));

    private static final List<Entry<String, String>> PREFIX_REASON_CODES = List.of(
            entry("Action requires subject.type=", "agent.action.subject_type.required"),
            entry("Invalid input for ", "agent.action.input.invalid"),
            entry("Unregistered scene: ", "agent.scene.unregistered"),
            entry("Unsupported backoffice subject: ", "agent.backoffice.subject.unsupported"),
            entry("Unsupported identity subject: ", "agent.identity.subject.unsupported"),
            entry("Unsupported intent: ", "agent.intent.unsupported"),
            entry("Unsupported market action: ", "agent.market.action.unsupported"),
            entry("Unsupported market subject: ", "agent.market.subject.unsupported"),
            entry("OKX Onchain Pay request failed: ", "payment.okx.request_failed"),
            entry("Unsupported workbench action: ", "workbench.action.unsupported"),
            entry("actionId is required for intent=act", "agent.action.id.required"));

    private static final Map<String, String> VALIDATION_CODES = Map.of(
            "NotBlank", "validation.required",
            "NotNull", "validation.required",
            "Size", "validation.length",
            "Pattern", "validation.pattern",
            "Min", "validation.min",
            "Positive", "validation.positive");

    private ApiErrorCodes() {
    }

    public static String fromReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return UNKNOWN;
        }
        if (EXACT_REASON_CODES.containsKey(reason)) {
            return EXACT_REASON_CODES.get(reason);
        }
        for (Entry<String, String> prefixEntry : PREFIX_REASON_CODES) {
            if (reason.startsWith(prefixEntry.getKey())) {
                return prefixEntry.getValue();
            }
        }
        // 中文注释：配置缺失文案既有单数服务，也有复数 credentials，统一返回前端可处理的 config.missing。
        if (reason.endsWith(" is not configured") || reason.endsWith(" are not configured")) {
            return "config.missing";
        }
        return "uncatalogued." + slugify(reason);
    }

    public static String fromFieldError(FieldError fieldError) {
        String defaultMessage = fieldError.getDefaultMessage();
        if (defaultMessage != null && CODE_PATTERN.matcher(defaultMessage).matches()) {
            return defaultMessage;
        }
        String resolvedFromReason = fromReason(defaultMessage);
        if (!resolvedFromReason.startsWith("uncatalogued.") && !resolvedFromReason.equals(UNKNOWN)) {
            return resolvedFromReason;
        }
        return VALIDATION_CODES.getOrDefault(fieldError.getCode(), VALIDATION_FAILED);
    }

    private static Entry<String, String> entry(String key, String value) {
        return Map.entry(key, value);
    }

    private static String slugify(String input) {
        String normalized = input.toLowerCase()
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }
}
