package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.MessageSource;

/**
 * One row of the tracking list as the panel gets it (SRS UC-AZS-13). {@code type} and {@code status} are the stable
 * codes; the labels are the Persian texts. Dates are Jalali {@code yyyy/MM/dd} (the panel shows them as they are);
 * {@code registeredAt} is also given as an exact instant.
 */
public record RequestSummaryResponse(UUID id, String trackingCode, String type, String typeLabel, String status,
        String statusLabel, String customerCode, String customerCif, String customerName, long amount,
        String branchCode, String branchName, String dueDate, Instant registeredAt, String registeredAtJalali,
        String agencyName, String customerMobile) {

    static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
    static final Locale FARSI = Locale.forLanguageTag("fa");

    static RequestSummaryResponse from(RequestSummary s, MessageSource messages) {
        return new RequestSummaryResponse(s.id(), s.trackingCode(), s.type().name(), label(messages, "type", s.type()),
                s.status().name(), label(messages, "status", s.status()), s.customerCode(), s.customerCif(),
                s.customerName(), s.amount(), s.branchCode(), s.branchName(),
                JalaliDate.fromGregorian(s.dueDate()).toSlashed(), s.registeredAt(), jalaliDateTime(s.registeredAt()),
                s.agencyName(), s.customerMobile());
    }

    static String label(MessageSource messages, String group, Enum<?> value) {
        return messages.getMessage("request." + group + "." + value.name(), null, value.name(), FARSI);
    }

    /** {@code yyyy/MM/dd HH:mm:ss} in Tehran time. */
    static String jalaliDateTime(Instant instant) {
        LocalDateTime local = instant.atZone(TEHRAN).toLocalDateTime();
        return JalaliDate.fromGregorian(local.toLocalDate()).toSlashed() + " "
                + String.format("%02d:%02d:%02d", local.getHour(), local.getMinute(), local.getSecond());
    }
}
