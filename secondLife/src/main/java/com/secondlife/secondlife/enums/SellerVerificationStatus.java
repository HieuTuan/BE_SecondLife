package com.secondlife.secondlife.enums;

import java.util.Set;

public enum SellerVerificationStatus {
    SUBMITTED,
    EKYC_PENDING,
    RESUBMIT_REQUIRED,
    NEEDS_REVIEW,
    APPROVED,
    REJECTED;

    public boolean canTransitionTo(SellerVerificationStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case SUBMITTED -> Set.of(EKYC_PENDING, RESUBMIT_REQUIRED, NEEDS_REVIEW, APPROVED, REJECTED).contains(target);
            case EKYC_PENDING -> Set.of(RESUBMIT_REQUIRED, NEEDS_REVIEW, APPROVED, REJECTED).contains(target);
            case RESUBMIT_REQUIRED -> Set.of(SUBMITTED, EKYC_PENDING).contains(target);
            case NEEDS_REVIEW -> Set.of(APPROVED, REJECTED).contains(target);
            case APPROVED, REJECTED -> false;
        };
    }
}
