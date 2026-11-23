package com.sb24.asanzeman.inquiry.infrastructure.external;

/** The keys, in {@code messages_fa.properties}, of the SRS texts a customer sees when an inquiry rejects (5.3). */
final class Messages {

    /** One text for four inquiries: «به دلیل محدودیت‌های بانک مرکزی…» */
    static final String CENTRAL_BANK = "inquiry.central-bank-restriction";
    static final String BOUNCED_CHEQUE = "inquiry.sama-cheque.bounced";
    static final String NON_CURRENT_LOAN = "inquiry.samat.non-current";

    private Messages() {
    }
}
