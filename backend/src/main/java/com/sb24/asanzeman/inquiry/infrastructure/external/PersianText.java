package com.sb24.asanzeman.inquiry.infrastructure.external;

/** Makes two spellings of the same Persian text equal (5.3): the bank's services mix Arabic and Persian letters. */
final class PersianText {

    private PersianText() {
    }

    /** Arabic ي and ك become ی and ک, half-spaces and runs of spaces become one space, and a final dot goes. */
    static String normalise(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace('ي', 'ی').replace('ك', 'ک').replace('‌', ' ').replaceAll("\\s+", " ").trim();
        while (t.endsWith(".")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }
}
