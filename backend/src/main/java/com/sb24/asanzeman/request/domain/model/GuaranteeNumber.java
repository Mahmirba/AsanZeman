package com.sb24.asanzeman.request.domain.model;

/**
 * The number Farangin gave the guarantee, which the branch user types in when it is issued (SRS: «شماره ضمانتنامه»).
 * Letters, digits, dash, slash and underscore, 1 to 30 characters.
 *
 * <p><b>Assumption to confirm:</b> the plan asks for "format validation" but gives no format, so this is the loosest
 * rule that still catches a slip such as spaces or a pasted sentence.
 */
public record GuaranteeNumber(String value) {

    private static final java.util.regex.Pattern FORMAT = java.util.regex.Pattern.compile("[0-9A-Za-z/_-]{1,30}");

    public GuaranteeNumber {
        value = value == null ? "" : value.trim();
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidRequestDataException("the guarantee number must be 1 to 30 letters, digits, - / or _");
        }
    }
}
