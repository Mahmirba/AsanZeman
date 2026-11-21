package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.MessageSource;

/**
 * The tracking export as CSV (D42, SRS UC-AZS-13 alternative flow 4): UTF-8 with a byte order mark so Excel opens the
 * Persian text correctly, written page by page so memory stays bounded. Cells that a spreadsheet would read as a
 * formula are prefixed with an apostrophe, so a customer name like {@code =HYPERLINK(...)} stays text.
 */
public class RequestCsvWriter implements AutoCloseable {

    static final String[] HEADER = {"کد ملی مشتری", "شماره مشتری", "نام و نام خانوادگی", "مبلغ ضمانت‌نامه (ریال)",
            "کد شعبه", "نام شعبه", "تاریخ سررسید ضمانت‌نامه", "تاریخ و زمان ثبت درخواست توسط آژانس", "نام آژانس",
            "نوع درخواست", "وضعیت درخواست", "کد رهگیری", "شماره تلفن همراه مشتری"};

    private final Writer out;
    private final MessageSource messages;

    public RequestCsvWriter(OutputStream stream, MessageSource messages) throws IOException {
        this.out = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        this.messages = messages;
        out.write('﻿');
        row(List.of(HEADER));
    }

    public void write(List<RequestSummary> page) throws IOException {
        for (RequestSummary s : page) {
            row(List.of(s.customerCode(), nullToEmpty(s.customerCif()), nullToEmpty(s.customerName()),
                    Long.toString(s.amount()), s.branchCode(), s.branchName(),
                    JalaliDate.fromGregorian(s.dueDate()).toSlashed(),
                    RequestSummaryResponse.jalaliDateTime(s.registeredAt()), s.agencyName(),
                    RequestSummaryResponse.label(messages, "type", s.type()),
                    RequestSummaryResponse.label(messages, "status", s.status()), s.trackingCode(),
                    nullToEmpty(s.customerMobile())));
        }
        out.flush();
    }

    private void row(List<String> cells) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(cell(cells.get(i)));
        }
        out.write(line.append("\r\n").toString());
    }

    static String cell(String value) {
        String text = value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void close() throws IOException {
        out.flush();
    }
}
