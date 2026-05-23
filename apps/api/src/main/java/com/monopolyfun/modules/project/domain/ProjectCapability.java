package com.monopolyfun.modules.project.domain;

public enum ProjectCapability {
    PROJECT_PARTICIPATE("project.participate"),
    PROJECT_LEAD("project.lead"),
    PROJECT_MANAGE("project.manage"),
    ROLE_ASSIGN("role.assign"),
    ROLE_VACATE("role.vacate"),
    ORDER_REVIEW("order.review"),
    ORDER_DISPUTE_RESOLVE("order.dispute.resolve"),
    MARKET_QUALITY_MANAGE("market.quality.manage"),
    PROJECT_TECH_MANAGE("project.tech.manage"),
    RUNTIME_MANAGE("runtime.manage"),
    PROOF_TECH_REVIEW("proof.tech.review"),
    UPLOAD_REVIEW("upload.review"),
    BACKOFFICE_VIEW("backoffice.view"),
    SECURITY_PASSWORD_RESET_ISSUE("security.password_reset.issue"),
    SECURITY_RISK_VIEW("security.risk.view"),
    SECURITY_RISK_MANAGE("security.risk.manage"),
    PAYMENT_REVIEW("payment.review"),
    PAYMENT_REFUND("payment.refund"),
    SETTLEMENT_MANAGE("settlement.manage"),
    COMPENSATION_APPROVE("compensation.approve"),
    MARKET_GROWTH_MANAGE("market.growth.manage"),
    LISTING_PROMOTE("listing.promote"),
    PARTNER_MANAGE("partner.manage");

    private final String code;

    ProjectCapability(String code) {
        this.code = code;
    }

    public static ProjectCapability fromCode(String code) {
        for (ProjectCapability capability : values()) {
            if (capability.code.equals(code)) {
                return capability;
            }
        }
        throw new IllegalArgumentException("Unknown project capability: " + code);
    }

    public String code() {
        return code;
    }
}
