package com.monopolyfun.modules.order.domain;

public enum OrderAction {
    CLAIM,
    SUBMIT_PROGRESS,
    SUBMIT_PROOF,
    SUBMIT_DELIVERY_RESULT,
    REQUEST_REVISION,
    ACCEPT,
    ASSIGN_REVIEWER,
    OVERRIDE_REVIEW,
    OPEN_DISPUTE,
    CANCEL_DISPUTE,
    OPEN_APPEAL,
    CLOSE
}
