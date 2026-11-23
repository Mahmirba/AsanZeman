package com.sb24.asanzeman.inquiry.domain.model;

/**
 * One inquiry's answer. {@code messageKey} is the SRS text to show when the result is {@code NEGATIVE};
 * {@code failure} says why when it is {@code FAILED}; {@code civil} is only set by Sabte Ahval.
 */
public record InquiryOutcome(InquiryType type, InquiryResult result, String messageKey, FailureKind failure,
        CivilData civil) {

    public InquiryOutcome {
        if (type == null || result == null) {
            throw new IllegalArgumentException("an outcome needs a type and a result");
        }
        if (result == InquiryResult.NEGATIVE && (messageKey == null || messageKey.isBlank())) {
            throw new IllegalArgumentException("a negative outcome needs the message to show");
        }
        if (result == InquiryResult.FAILED && failure == null) {
            throw new IllegalArgumentException("a failed outcome needs its failure kind");
        }
    }

    public static InquiryOutcome passed(InquiryType type) {
        return new InquiryOutcome(type, InquiryResult.PASSED, null, null, null);
    }

    /** Sabte Ahval passed: the civil data goes on to stage 2. */
    public static InquiryOutcome passed(InquiryType type, CivilData civil) {
        return new InquiryOutcome(type, InquiryResult.PASSED, null, null, civil);
    }

    public static InquiryOutcome negative(InquiryType type, String messageKey) {
        return new InquiryOutcome(type, InquiryResult.NEGATIVE, messageKey, null, null);
    }

    public static InquiryOutcome failed(InquiryType type, FailureKind failure) {
        return new InquiryOutcome(type, InquiryResult.FAILED, null, failure, null);
    }

    public static InquiryOutcome skipped(InquiryType type) {
        return new InquiryOutcome(type, InquiryResult.SKIPPED, null, null, null);
    }
}
