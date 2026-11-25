package com.sb24.asanzeman.request.domain.service;

/**
 * The salutation line of the SMS texts (6.4): «آقا» or «خانم» and the customer name, from the gender the civil registry
 * gave. When the gender is not known the line is left out and the text starts with «مشتری گرامی».
 */
public final class Salutation {

    private Salutation() {
    }

    /**
     * @param gender {@code MALE} or {@code FEMALE}, or null when not known
     * @return the line with its line break, or an empty string when there is no gender or no name
     */
    public static String line(String gender, String fullName) {
        String name = fullName == null ? "" : fullName.trim();
        if (gender == null || name.isEmpty()) {
            return "";
        }
        return ("FEMALE".equals(gender) ? "خانم " : "آقا ") + name + "\n";
    }
}
