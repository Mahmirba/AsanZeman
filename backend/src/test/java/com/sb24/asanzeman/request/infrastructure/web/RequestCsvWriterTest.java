package com.sb24.asanzeman.request.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

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

class RequestCsvWriterTest {

    private static ResourceBundleMessageSource messages() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static RequestSummary row(String name, String cif, String mobile) {
        return new RequestSummary(UUID.randomUUID(), "012300420508300001", RequestType.ISSUANCE,
                RequestStatus.CANCELLED, "1000000011", cif, name, 250_000_000L, "0123", "Valiasr",
                LocalDate.of(2027, 1, 21), Instant.parse("2026-11-21T10:00:00Z"), "Sky Travel", mobile);
    }

    private static String csv(RequestSummary... rows) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var writer = new RequestCsvWriter(out, messages())) {
            writer.write(List.of(rows));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void startsWithTheByteOrderMarkAndThePersianHeader() throws Exception {
        String text = csv();

        assertThat(text).startsWith("﻿").contains("کد ملی مشتری,شماره مشتری,نام و نام خانوادگی")
                .endsWith("شماره تلفن همراه مشتری\r\n");
    }

    @Test
    void aRowHasEveryColumnWithJalaliDatesAndPersianLabels() throws Exception {
        String line = csv(row("Ali Rezaei", "555", "09121234567")).split("\r\n")[1];

        assertThat(line).isEqualTo("1000000011,555,Ali Rezaei,250000000,0123,Valiasr,1405/11/01,"
                + "1405/08/30 13:30:00,Sky Travel,صدور ضمانت‌نامه,لغو شده,012300420508300001,09121234567");
    }

    @Test
    void missingValuesAreEmptyCells() throws Exception {
        String line = csv(row(null, null, null)).split("\r\n")[1];

        assertThat(line).startsWith("1000000011,,,250000000,").endsWith(",012300420508300001,");
    }

    @Test
    void commasQuotesAndNewlinesAreQuoted() {
        assertThat(RequestCsvWriter.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(RequestCsvWriter.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(RequestCsvWriter.cell("line1\nline2")).isEqualTo("\"line1\nline2\"");
        assertThat(RequestCsvWriter.cell("plain")).isEqualTo("plain");
        assertThat(RequestCsvWriter.cell("")).isEmpty();
    }

    @Test
    void aCellASpreadsheetWouldReadAsAFormulaStaysText() {
        assertThat(RequestCsvWriter.cell("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(RequestCsvWriter.cell("+1")).isEqualTo("'+1");
        assertThat(RequestCsvWriter.cell("-1")).isEqualTo("'-1");
        assertThat(RequestCsvWriter.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(RequestCsvWriter.cell("\tx")).isEqualTo("'\tx");
    }
}
