package com.sb24.asanzeman.notification.domain.port.in;

/**
 * The notification SMS texts (6.4). Each text lives in the message config under {@link #messageKey()}, filled with
 * the arguments the caller passes. More templates arrive with their events (issued, rejected, revoked, ...).
 */
public enum SmsTemplate {
    /** Event 1: an agency registered a request; args: the agency name. */
    REQUEST_REGISTERED("sms.request-registered"),
    /** Event 6: the agency cancelled the request before «اقدام»; args: the agency name, the Jalali date. */
    REQUEST_CANCELLED("sms.request-cancelled"),
    /** The branch rejected the request; args: the agency name, the branch name, the Jalali date. */
    REQUEST_REJECTED("sms.request-rejected"),
    /** The guarantee was issued; args: the agency name, the branch name, the Jalali date. */
    GUARANTEE_ISSUED("sms.guarantee-issued");

    private final String messageKey;

    SmsTemplate(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
