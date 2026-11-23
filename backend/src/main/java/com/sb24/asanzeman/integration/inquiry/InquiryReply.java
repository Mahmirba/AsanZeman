package com.sb24.asanzeman.integration.inquiry;

/** What an inquiry service answered: the HTTP status and the whole body, which the caller reads and keeps (D22). */
public record InquiryReply(int status, String body) {
}
