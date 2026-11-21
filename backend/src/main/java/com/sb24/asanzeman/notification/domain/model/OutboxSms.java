package com.sb24.asanzeman.notification.domain.model;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One message in the outbox: one recipient per row (6.4). The number is kept as saved at registration,
 * {@code 09xxxxxxxxx}; it is turned into {@code 0989xxxxxxxxx} only when sent.
 *
 * @param attempts how many times a send was tried, counting the one in progress
 */
public record OutboxSms(UUID id, String mobile, String templateCode, String text, UUID requestId, int attempts) {

    private static final Pattern MOBILE = Pattern.compile("^09[0-9]{9}$");

    public OutboxSms {
        if (mobile == null || !MOBILE.matcher(mobile).matches()) {
            throw new InvalidMobileException();
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("an SMS needs a text");
        }
    }

    /** {@code 09121234567} becomes {@code 0989121234567}: drop the leading 0 and put {@code 098} in front. */
    public String gatewayNumber() {
        return "098" + mobile.substring(1);
    }
}
