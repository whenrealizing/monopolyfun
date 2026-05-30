type ErrorContext = Record<string, string | number | boolean | null | undefined>;

type ErrorWithDetails = {
    code?: unknown;
    message?: unknown;
    fields?: unknown;
    context?: unknown;
};

type ErrorMessageEntry = string | ((context?: ErrorContext) => string);
type ErrorLocale = "zh-CN" | "en";

let activeErrorLocale: ErrorLocale = "zh-CN";

export function setErrorMessageLocale(locale: ErrorLocale) {
    activeErrorLocale = locale;
}

const ERROR_MESSAGES_ZH: Record<string, ErrorMessageEntry> = {
    "account.not_found": "未找到账号。",
    "agent.action.id.required": "缺少要执行的动作 ID。",
    "agent.action.input.invalid": "动作输入格式不正确。",
    "agent.action.subject_type.required": "当前动作缺少必需的对象类型。",
    "agent.backoffice.intent.view_only": "后台场景目前只支持查看。",
    "agent.backoffice.subject.unsupported": "后台场景暂不支持这个对象类型。",
    "agent.home.intent.view_only": "首页场景目前只支持查看。",
    "agent.identity.intent.view_only": "个人中心场景目前只支持查看。",
    "agent.identity.subject.unsupported": "个人中心场景暂不支持这个对象类型。",
    "agent.intent.required": "缺少意图。",
    "agent.intent.unsupported": "不支持这个意图。",
    "agent.market.action.unsupported": "不支持这个市场动作。",
    "agent.market.subject.unsupported": "不支持这个市场对象类型。",
    "agent.scene.required": "缺少场景。",
    "agent.scene.unregistered": "场景未注册。",
    "auth.actor.mismatch": "当前登录账号与请求操作人不一致。",
    "auth.handle.invalid_format": "账号名需为 3-20 位，只允许字母、数字、下划线和中横线。",
    "auth.handle.invalid_length": "账号名需为 3-20 个字符。",
    "auth.handle.invalid_pattern": "账号名只允许字母、数字、下划线和中横线。",
    "auth.handle.required": "请输入账号名。",
    "auth.handle.taken": "这个账号名已经被占用。",
    "auth.login.invalid_credentials": "账号名或密码错误。",
    "auth.login.password_unavailable": "该账号暂不支持密码登录。",
    "auth.login.rate_limited": "登录尝试过于频繁，请稍后再试。",
    "auth.password.invalid_length": "密码需为 8-120 个字符。",
    "auth.password.required": "请输入密码。",
    "auth.password_reset.password.required": "请输入新密码。",
    "auth.password_reset.rate_limited": "密码重置请求过于频繁，请稍后再试。",
    "auth.password_reset.token.invalid": "重置令牌无效。",
    "auth.password_reset.token.unusable": "重置令牌已过期或已被使用。",
    "auth.required": "请先登录后再继续操作。",
    "auth.token.required": "缺少登录凭证。",
    "common.action.failed": "操作失败，请稍后重试。",
    "config.missing": "服务端缺少必要配置。",
    "identity.profile.avatar_url.invalid_format": "头像链接需使用 http 或 https。",
    "identity.profile.avatar_url.invalid_length": "头像链接过长。",
    "identity.skin.update.failed": "展示皮肤设置失败。",
    "identity.skin.verify_before_use": (context) => `请先完成 ${context?.provider ?? "外部账号"} 认证，再把 @${context?.handle ?? ""} 设为展示皮肤。`,
    "identity.verification.start.failed": "认证暂时无法发起。",
    "listing.already_archived": "条目已经归档。",
    "listing.already_closed": "条目已经关闭。",
    "listing.claim.review_unsupported": "评审任务需要从评审入口领取。",
    "listing.claim.self_forbidden": "不能领取自己发布的交易项。",
    "listing.inventory.sold_out": "该交易项库存或名额已满。",
    "listing.inventory_limit.below_active_orders": "库存上限不能低于当前活跃订单数。",
    "listing.money_settlement.amount_required": "金钱结算的条目必须包含金额。",
    "listing.not_found": "未找到条目。",
    "listing.open.forbidden": "你没有权限创建条目。",
    "listing.opener_account.not_found": "未找到创建条目的账号。",
    "listing.publish.forbidden": "你没有权限发布条目。",
    "listing.publish.invalid_state": "当前条目状态不能发布。",
    "listing.reopen.invalid_state": "当前条目状态不能重新开放。",
    "listing.update.forbidden": "你没有权限更新条目。",
    "market.action.create_invalid": "当前接口不支持这个市场动作。",
    "market.action.update_invalid": "更新市场请使用更新接口。",
    "market.lead.already_owner": "负责人已经是市场所有者。",
    "market.lead_account.not_found": "未找到市场负责人账号。",
    "market.member.already_exists": "该成员已在市场中。",
    "market.member.not_found": "未找到市场成员。",
    "market.member_account.not_found": "未找到成员账号。",
    "market.member_add.forbidden": "你没有权限添加市场成员。",
    "market.member_remove.forbidden": "你没有权限移除市场成员。",
    "market.not_found": "未找到市场。",
    "market.status_change.forbidden": "你没有权限修改市场状态。",
    "market.update.forbidden": "你没有权限更新市场。",
    "order.accept.forbidden": "你没有权限验收这个订单。",
    "order.accept.invalid_state": "当前订单状态不能验收。",
    "order.active_for_account": "你已经有一笔进行中的订单，请先完成或处理该订单后再购买。",
    "order.already_terminal": "订单已经结束，不能重复操作。",
    "order.close.forbidden": "你没有权限关闭这个订单。",
    "order.payment_abandon.already_paid": "订单已经付款，不能放弃付款。",
    "order.payment_abandon.forbidden": "只有付款方可以放弃付款。",
    "order.payment_abandon.invalid_state": "当前订单状态不能放弃付款。",
    "order.payment_abandon.not_money_order": "只有金钱结算订单可以放弃付款。",
    "order.dispute.forbidden": "你没有权限发起争议。",
    "order.dispute.resolve.forbidden": "你没有权限处理这个争议订单。",
    "order.dispute.invalid_state": "当前订单状态不能发起争议。",
    "order.dispute.window_closed": "该订单的争议窗口已关闭。",
    "order.not_found": "未找到订单。",
    "order.delivery.reviewed_required": "当前订单不支持提交人工验收交付结果。",
    "order.delivery.submitter_not_fulfiller": "只有履约方可以提交交付结果。",
    "order.proof.artifact_registration_required": "提交证明前，附件必须先注册。",
    "order.proof.artifact_upload_incomplete": "附件上传尚未完成。",
    "order.proof.evidence_required": "请至少提供一个链接或附件作为证明。",
    "order.proof.criteria_required": "请至少引用一条验收标准。",
    "order.proof.invalid_state": "当前订单状态不能提交证明。",
    "order.proof.submitter_not_claimed_account": "只有领取该订单的账号才能提交证明。",
    "order.progress.submitter_not_fulfiller": "只有履约方可以提交进度。",
    "order.progress.unsupported": "当前订单不支持提交进度。",
    "order.review_proof.decision_required": "评审证明需要选择处理结论。",
    "order.review_assign.forbidden": "你没有权限指派评审人。",
    "order.review_assign.invalid_state": "当前订单状态不能指派评审人。",
    "order.review_override.forbidden": "你没有权限覆盖争议结果。",
    "order.review_override.invalid_state": "当前订单状态不能覆盖争议结果。",
    "order.settlement.frozen": "争议订单的结算已冻结，请等待处理结果。",
    "payment.amount_mismatch": "支付金额不匹配。",
    "payment.actor.order_buyer_required": "只有该订单的付款方可以创建支付。",
    "payment.callback.signature_invalid": "支付回调签名无效。",
    "payment.callback.status.unsupported": "不支持这个支付回调状态。",
    "payment.callback.token_mismatch": "支付回调令牌不匹配。",
    "payment.intent.invalid_order_state": "当前订单状态不能创建支付。",
    "payment.intent.not_found": "未找到支付意图。",
    "payment.money_order.amount_required": "金钱订单必须有正数金额。",
    "payment.money_settlement.capture_required": "金钱结算要求支付已授权或已完成。",
    "payment.money_settlement.intent_required": "金钱结算要求先创建支付意图。",
    "payment.order.not_money_settlement": "当前订单不是金钱结算类型。",
    "payment.settlement.frozen": "争议订单的支付结算已冻结，请等待处理结果。",
    "payment.transition.forbidden": "你没有权限更新这个支付状态。",
    "project.role.member_redundant": "该账号已有项目职位，成员资格已由职位自动获得。",
    "resource.not_found": "未找到请求的资源。",
    "server.internal": "服务器内部错误，请稍后重试。",
    "ui.agent.action.failed": "我的智能体动作执行失败。",
    "ui.agent.turn.failed": "我的智能体回合加载失败。",
    "ui.auth.failed": "登录或注册失败。",
    "ui.listing.create.failed": "创建条目失败。",
    "ui.listing.edit.failed": "更新条目失败。",
    "ui.listing.state.failed": "条目状态更新失败。",
    "ui.market.create.failed": "创建市场失败。",
    "ui.market.edit.failed": "更新市场失败。",
    "ui.market.member.add.failed": "添加成员失败。",
    "ui.market.member.remove.failed": "移除成员失败。",
    "ui.order.command.failed": "订单操作失败。",
    "ui.order.reviewer.required": "请选择评审人。",
    "ui.password_reset.confirm.failed": "重置密码失败。",
    "ui.password_reset.request.failed": "请求重置失败。",
    "ui.upload.failed": (context) => `附件上传失败${context?.status ? `（${context.status}）` : ""}。`,
    "upload.asset.invalid_state": "上传资产当前不是待上传状态。",
    "upload.asset.not_found": "未找到上传资产。",
    "upload.checksum.invalid_format": "文件校验值格式不正确。",
    "upload.checksum_mismatch": "文件校验值不匹配。",
    "upload.content_length.exceeded": "上传文件超过大小限制。",
    "upload.content_length_mismatch": "上传文件大小不匹配。",
    "upload.content_type.unsupported": "不支持这个文件类型。",
    "upload.content_type_mismatch": "上传文件类型不匹配。",
    "upload.presign.rate_limited": "上传请求过于频繁，请稍后再试。",
    "validation.failed": "提交内容校验失败，请检查输入。",
    "validation.length": "输入长度不符合要求。",
    "validation.min": "输入值低于允许范围。",
    "validation.pattern": "输入格式不正确。",
    "validation.positive": "请输入大于 0 的数值。",
    "validation.required": "请填写此项。",
    "workbench.action.unsupported": "不支持这个工作台动作。",
    "workbench.item.dismiss_forbidden": "这条待办需要处理，不能隐藏。",
    "workbench.item.not_found": "未找到工作台项目。",
};

const ERROR_MESSAGES_EN: Record<string, ErrorMessageEntry> = {
    "account.not_found": "Account not found.",
    "agent.action.id.required": "Choose an action before running My Agent.",
    "agent.action.input.invalid": "The action input is not valid JSON.",
    "agent.action.subject_type.required": "This action needs a subject type.",
    "agent.backoffice.intent.view_only": "Backoffice scenes are view-only for now.",
    "agent.backoffice.subject.unsupported": "This backoffice subject is not supported yet.",
    "agent.home.intent.view_only": "The home scene is view-only for now.",
    "agent.identity.intent.view_only": "Profile scenes are view-only for now.",
    "agent.identity.subject.unsupported": "This profile subject is not supported yet.",
    "agent.intent.required": "Missing intent.",
    "agent.intent.unsupported": "This intent is not supported.",
    "agent.market.action.unsupported": "This marketplace action is not supported.",
    "agent.market.subject.unsupported": "This marketplace subject is not supported.",
    "agent.scene.required": "Missing scene.",
    "agent.scene.unregistered": "This scene is not registered.",
    "auth.actor.mismatch": "You are signed in as a different account.",
    "auth.handle.invalid_format": "Use 3-20 letters, numbers, underscores, or hyphens.",
    "auth.handle.invalid_length": "Use 3-20 characters.",
    "auth.handle.invalid_pattern": "Use only letters, numbers, underscores, or hyphens.",
    "auth.handle.required": "Enter a username.",
    "auth.handle.taken": "This username is already taken.",
    "auth.login.invalid_credentials": "Username or password is incorrect.",
    "auth.login.password_unavailable": "This account does not support password sign-in yet.",
    "auth.login.rate_limited": "Too many sign-in attempts. Try again later.",
    "auth.password.invalid_length": "Use 8-120 characters.",
    "auth.password.required": "Enter a password.",
    "auth.password_reset.password.required": "Enter a new password.",
    "auth.password_reset.rate_limited": "Too many reset requests. Try again later.",
    "auth.password_reset.token.invalid": "The reset token is invalid.",
    "auth.password_reset.token.unusable": "The reset token expired or was already used.",
    "auth.required": "Sign in to continue.",
    "auth.token.required": "Missing sign-in credentials.",
    "common.action.failed": "Action failed. Try again later.",
    "config.missing": "The server is missing required configuration.",
    "identity.profile.avatar_url.invalid_format": "Avatar URL must use http or https.",
    "identity.profile.avatar_url.invalid_length": "Avatar URL is too long.",
    "identity.skin.update.failed": "Could not update the display skin.",
    "identity.skin.verify_before_use": (context) => `Verify ${context?.provider ?? "the external account"} before using @${context?.handle ?? ""} as your display skin.`,
    "identity.verification.start.failed": "Could not start verification.",
    "listing.already_archived": "This listing is already archived.",
    "listing.already_closed": "This listing is already closed.",
    "listing.claim.review_unsupported": "Review tasks must be claimed from the review flow.",
    "listing.claim.self_forbidden": "You cannot claim your own listing item.",
    "listing.inventory.sold_out": "This item is out of stock or slots.",
    "listing.inventory_limit.below_active_orders": "Inventory cannot be lower than active orders.",
    "listing.money_settlement.amount_required": "Cash listings need an amount.",
    "listing.not_found": "Listing not found.",
    "listing.open.forbidden": "You do not have permission to open this listing.",
    "listing.opener_account.not_found": "The listing creator account was not found.",
    "listing.publish.forbidden": "You do not have permission to publish this listing.",
    "listing.publish.invalid_state": "This listing cannot be published in its current state.",
    "listing.reopen.invalid_state": "This listing cannot be reopened in its current state.",
    "listing.update.forbidden": "You do not have permission to update this listing.",
    "market.action.create_invalid": "This marketplace action cannot be created here.",
    "market.action.update_invalid": "Use the update endpoint to edit a market.",
    "market.lead.already_owner": "The lead is already the market owner.",
    "market.lead_account.not_found": "Market lead account not found.",
    "market.member.already_exists": "This member is already in the market.",
    "market.member.not_found": "Market member not found.",
    "market.member_account.not_found": "Member account not found.",
    "market.member_add.forbidden": "You do not have permission to add market members.",
    "market.member_remove.forbidden": "You do not have permission to remove market members.",
    "market.not_found": "Market not found.",
    "market.status_change.forbidden": "You do not have permission to change market status.",
    "market.update.forbidden": "You do not have permission to update this market.",
    "order.accept.forbidden": "You do not have permission to accept this order.",
    "order.accept.invalid_state": "This order cannot be accepted right now.",
    "order.active_for_account": "You already have an active order. Finish or resolve it before buying again.",
    "order.already_terminal": "This order is already closed.",
    "order.close.forbidden": "You do not have permission to close this order.",
    "order.payment_abandon.already_paid": "This order has already been paid and cannot be abandoned.",
    "order.payment_abandon.forbidden": "Only the payer can abandon payment.",
    "order.payment_abandon.invalid_state": "This order cannot abandon payment in its current state.",
    "order.payment_abandon.not_money_order": "Only cash orders can abandon payment.",
    "order.dispute.forbidden": "You do not have permission to open a dispute.",
    "order.dispute.resolve.forbidden": "You do not have permission to resolve this dispute.",
    "order.dispute.invalid_state": "This order cannot enter dispute right now.",
    "order.dispute.window_closed": "The dispute window for this order has closed.",
    "order.not_found": "Order not found.",
    "order.delivery.reviewed_required": "This order does not support reviewed delivery submission.",
    "order.delivery.submitter_not_fulfiller": "Only the fulfiller can submit delivery.",
    "order.proof.artifact_registration_required": "Register the attachment before submitting proof.",
    "order.proof.artifact_upload_incomplete": "The attachment upload is not complete.",
    "order.proof.evidence_required": "Add at least one link or attachment as proof.",
    "order.proof.criteria_required": "Reference at least one acceptance criterion.",
    "order.proof.invalid_state": "Proof cannot be submitted in this order state.",
    "order.proof.submitter_not_claimed_account": "Only the account that claimed this order can submit proof.",
    "order.progress.submitter_not_fulfiller": "Only the fulfiller can submit progress.",
    "order.progress.unsupported": "This order does not support progress updates.",
    "order.review_proof.decision_required": "Choose a review decision before submitting proof.",
    "order.review_assign.forbidden": "You do not have permission to assign a reviewer.",
    "order.review_assign.invalid_state": "A reviewer cannot be assigned in this order state.",
    "order.review_override.forbidden": "You do not have permission to override this dispute.",
    "order.review_override.invalid_state": "This dispute cannot be overridden in its current state.",
    "order.settlement.frozen": "This disputed order settlement is frozen until review resolves it.",
    "payment.amount_mismatch": "Payment amount does not match.",
    "payment.actor.order_buyer_required": "Only the payer for this order can create payment.",
    "payment.callback.signature_invalid": "Payment callback signature is invalid.",
    "payment.callback.status.unsupported": "This payment callback status is not supported.",
    "payment.callback.token_mismatch": "Payment callback token does not match.",
    "payment.intent.invalid_order_state": "A payment intent cannot be created for this order state.",
    "payment.intent.not_found": "Payment intent not found.",
    "payment.money_order.amount_required": "Cash orders need a positive amount.",
    "payment.money_settlement.capture_required": "Cash settlement requires an authorized or captured payment.",
    "payment.money_settlement.intent_required": "Create a payment intent before cash settlement.",
    "payment.order.not_money_settlement": "This order is not settled in cash.",
    "payment.settlement.frozen": "This disputed order payment settlement is frozen until review resolves it.",
    "payment.transition.forbidden": "You do not have permission to update this payment status.",
    "project.role.member_redundant": "This account already has a project role, so membership is automatic.",
    "resource.not_found": "The requested resource was not found.",
    "server.internal": "Server error. Try again later.",
    "ui.agent.action.failed": "My Agent could not run the action.",
    "ui.agent.turn.failed": "My Agent could not load the turn.",
    "ui.auth.failed": "Sign-in or registration failed.",
    "ui.listing.create.failed": "Could not create the listing.",
    "ui.listing.edit.failed": "Could not update the listing.",
    "ui.listing.state.failed": "Could not update listing status.",
    "ui.market.create.failed": "Could not create the market.",
    "ui.market.edit.failed": "Could not update the market.",
    "ui.market.member.add.failed": "Could not add the member.",
    "ui.market.member.remove.failed": "Could not remove the member.",
    "ui.order.command.failed": "Order action failed.",
    "ui.order.reviewer.required": "Choose a reviewer.",
    "ui.password_reset.confirm.failed": "Could not reset the password.",
    "ui.password_reset.request.failed": "Could not request a reset.",
    "ui.upload.failed": (context) => `Attachment upload failed${context?.status ? ` (${context.status})` : ""}.`,
    "upload.asset.invalid_state": "This asset is not waiting for upload.",
    "upload.asset.not_found": "Upload asset not found.",
    "upload.checksum.invalid_format": "File checksum format is invalid.",
    "upload.checksum_mismatch": "File checksum does not match.",
    "upload.content_length.exceeded": "The file is too large.",
    "upload.content_length_mismatch": "File size does not match.",
    "upload.content_type.unsupported": "This file type is not supported.",
    "upload.content_type_mismatch": "File type does not match.",
    "upload.presign.rate_limited": "Too many upload requests. Try again later.",
    "validation.failed": "Validation failed. Check the input.",
    "validation.length": "Input length is not allowed.",
    "validation.min": "The value is below the allowed minimum.",
    "validation.pattern": "Input format is invalid.",
    "validation.positive": "Enter a value greater than 0.",
    "validation.required": "Fill in this field.",
    "workbench.action.unsupported": "This workbench action is not supported.",
    "workbench.item.dismiss_forbidden": "This task must be handled and cannot be hidden.",
    "workbench.item.not_found": "Workbench item not found.",
};

function activeErrorMessages() {
    return activeErrorLocale === "en" ? ERROR_MESSAGES_EN : ERROR_MESSAGES_ZH;
}

export class UiError extends Error {
    code: string;
    context?: ErrorContext;

    constructor(code: string, context?: ErrorContext, fallbackMessage?: string) {
        super(fallbackMessage ?? code);
        this.name = "UiError";
        this.code = code;
        this.context = context;
    }
}

export function resolveErrorMessage(code?: string, fallbackMessage?: string, context?: ErrorContext) {
    const messages = activeErrorMessages();
    if (code) {
        const entry = messages[code];
        if (typeof entry === "function") {
            return entry(context);
        }
        if (typeof entry === "string") {
            return entry;
        }
        if (code.startsWith("uncatalogued.")) {
            return fallbackMessage && fallbackMessage !== code ? fallbackMessage : genericError("actionFailed");
        }
        if (code.endsWith(".not_found")) {
            return genericError("notFound");
        }
        if (code.endsWith(".forbidden")) {
            return genericError("forbidden");
        }
        if (code.endsWith(".unsupported")) {
            return genericError("unsupported");
        }
        if (code.endsWith(".required")) {
            return genericError("required");
        }
        if (code.endsWith(".invalid_state")) {
            return genericError("invalidState");
        }
    }
    if (fallbackMessage && fallbackMessage !== code) {
        return fallbackMessage;
    }
    return genericError("actionFailed");
}

function genericError(kind: "actionFailed" | "notFound" | "forbidden" | "unsupported" | "required" | "invalidState") {
    const zh = {
        actionFailed: "操作失败，请稍后重试。",
        notFound: "未找到对应数据。",
        forbidden: "你没有权限执行这个操作。",
        unsupported: "当前操作不受支持。",
        required: "请填写必需内容。",
        invalidState: "当前状态下无法执行这个操作。",
    } as const;
    const en = {
        actionFailed: "Action failed. Try again later.",
        notFound: "No matching data was found.",
        forbidden: "You do not have permission to perform this action.",
        unsupported: "This action is not supported.",
        required: "Fill in the required information.",
        invalidState: "This action is not available in the current state.",
    } as const;
    return activeErrorLocale === "en" ? en[kind] : zh[kind];
}

const RAW_MESSAGE_ERROR_CODES: Record<string, string> = {};

function inferErrorCodeFromMessage(message?: string) {
    return message ? RAW_MESSAGE_ERROR_CODES[message] : undefined;
}

export function presentError(error: unknown, fallbackCode = "common.action.failed") {
    const details = asErrorDetails(error);
    const rawMessage = asString(details?.message);
    const rawCode = asString(details?.code) ?? inferErrorCodeFromMessage(rawMessage);
    const context = asContext(details?.context);
    const fields = asFields(details?.fields);

    const fieldErrors = Object.fromEntries(
        Object.entries(fields).map(([field, code]) => [field, resolveErrorMessage(code, undefined, context)]),
    );

    return {
        code: rawCode ?? fallbackCode,
        message: rawCode
            ? resolveErrorMessage(rawCode, rawMessage, context)
            : rawMessage ?? resolveErrorMessage(fallbackCode),
        fieldErrors,
    };
}

function asErrorDetails(error: unknown): ErrorWithDetails | null {
    return typeof error === "object" && error !== null ? error as ErrorWithDetails : null;
}

function asString(value: unknown) {
    return typeof value === "string" && value.trim() ? value : undefined;
}

function asFields(value: unknown): Record<string, string> {
    if (!value || typeof value !== "object") {
        return {};
    }
    return Object.fromEntries(
        Object.entries(value as Record<string, unknown>).filter(([, fieldCode]) => typeof fieldCode === "string"),
    ) as Record<string, string>;
}

function asContext(value: unknown): ErrorContext | undefined {
    return value && typeof value === "object" ? value as ErrorContext : undefined;
}
