package com.sb24.asanzeman.auth.domain.model;

import java.util.Locale;

/**
 * A bank user's {@code accountName} (D33): the login name the user typed, without the {@code @sb24.ir} suffix. It is
 * what personnelInfo is asked for, and the display name of the lock holder in the inbox.
 */
public record AccountName(String value) {

    public AccountName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("accountName is required");
        }
    }

    /**
     * @param login  what the user typed, e.g. {@code ali.rezaei@sb24.ir}
     * @param suffix the suffix to remove (configurable), e.g. {@code @sb24.ir}; {@code null} or blank removes nothing
     */
    public static AccountName fromLogin(String login, String suffix) {
        if (login == null) {
            throw new IllegalArgumentException("accountName is required");
        }
        String name = login.trim();
        if (suffix != null && !suffix.isBlank()
                && name.toLowerCase(Locale.ROOT).endsWith(suffix.trim().toLowerCase(Locale.ROOT))) {
            name = name.substring(0, name.length() - suffix.trim().length()).trim();
        }
        return new AccountName(name);
    }
}
