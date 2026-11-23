package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.BranchRequestRow;
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
 * The bank's report as CSV (D42): the tracking columns, then the original branch and the branch that handles the
 * request now (4.4), the guarantee number, and the rejection reason and comment. UTF-8 with a byte order mark, written
 * page by page; a cell a spreadsheet could read as a formula is made text ({@link RequestCsvWriter#cell}).
 */
public class BranchCsvWriter implements AutoCloseable {

    static final String[] HEADER = {"کد ملی مشتری", "شماره مشتری", "نام و نام خانوادگی", "مبلغ ضمانت‌نامه (ریال)",
            "کد شعبه", "نام شعبه", "شعبه رسیدگی‌کننده", "تاریخ سررسید ضمانت‌نامه", "تاریخ و زمان ثبت درخواست توسط آژانس",
            "نام آژانس", "نوع درخواست", "وضعیت درخواست", "کد رهگیری", "شماره تلفن همراه مشتری",
            "شماره ضمانت‌نامه", "دلیل رد", "توضیحات رد"};

    private final Writer out;
    private final MessageSource messages;

    public BranchCsvWriter(OutputStream stream, MessageSource messages) throws IOException {
        this.out = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        this.messages = messages;
        out.write('﻿');
        row(List.of(HEADER));
    }

    public void write(List<BranchRequestRow> page) throws IOException {
        for (BranchRequestRow r : page) {
            RequestSummary s = r.summary();
            row(List.of(s.customerCode(), blank(s.customerCif()), blank(s.customerName()), Long.toString(s.amount()),
                    s.branchCode(), s.branchName(), blank(r.handlingBranch()),
                    JalaliDate.fromGregorian(s.dueDate()).toSlashed(),
                    RequestSummaryResponse.jalaliDateTime(s.registeredAt()), s.agencyName(),
                    RequestSummaryResponse.label(messages, "type", s.type()),
                    RequestSummaryResponse.label(messages, "status", s.status()), s.trackingCode(),
                    blank(s.customerMobile()), blank(r.guaranteeNumber()), blank(r.rejectionReason()),
                    blank(r.rejectionComment())));
        }
        out.flush();
    }

    private void row(List<String> cells) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(RequestCsvWriter.cell(cells.get(i)));
        }
        out.write(line.append("\r\n").toString());
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void close() throws IOException {
        out.flush();
    }
}
