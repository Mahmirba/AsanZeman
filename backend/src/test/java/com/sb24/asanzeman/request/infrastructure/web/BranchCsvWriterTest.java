package com.sb24.asanzeman.request.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.request.domain.model.BranchRequestRow;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.request.domain.model.RequestType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

class BranchCsvWriterTest {

    private static ResourceBundleMessageSource messages() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static BranchRequestRow row(String handling, String number, String reason, String comment) {
        var summary = new RequestSummary(UUID.randomUUID(), "012300420508300001", RequestType.ISSUANCE,
                RequestStatus.REVOKED, "1000000011", null, "=HYPERLINK(1)", 250_000_000L, "0123", "Valiasr",
                LocalDate.of(2027, 1, 21), Instant.parse("2026-11-21T10:00:00Z"), "Sky Travel", null);
        return new BranchRequestRow(summary, null, null, handling, reason, comment, number);
    }

    private static String csv(BranchRequestRow... rows) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var writer = new BranchCsvWriter(out, messages())) {
            writer.write(List.of(rows));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void theHeaderStartsWithTheByteOrderMarkAndHasTheReportColumns() throws Exception {
        assertThat(csv()).startsWith("﻿").contains("شعبه رسیدگی‌کننده").endsWith("توضیحات رد\r\n");
    }

    @Test
    void aRowHasTheHandlingBranchTheGuaranteeNumberAndTheRejection() throws Exception {
        String line = csv(row("0456", "GT-001", "Incomplete", "needs, a \"stamp\"")).split("\r\n")[1];

        assertThat(line).isEqualTo("1000000011,,'=HYPERLINK(1),250000000,0123,Valiasr,0456,1405/11/01,"
                + "1405/08/30 13:30:00,Sky Travel,صدور ضمانت‌نامه,ابطال شده,012300420508300001,,GT-001,Incomplete,"
                + "\"needs, a \"\"stamp\"\"\"");
    }

    @Test
    void missingValuesAreEmptyCells() throws Exception {
        String line = csv(row(null, null, null, null)).split("\r\n")[1];

        assertThat(line).endsWith("012300420508300001,,,,");
    }
}
