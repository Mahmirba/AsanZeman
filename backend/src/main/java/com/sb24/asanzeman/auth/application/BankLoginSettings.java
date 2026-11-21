package com.sb24.asanzeman.auth.application;

/** The suffix removed from a bank login to get the {@code accountName} (D33), e.g. {@code @sb24.ir}. */
public record BankLoginSettings(String accountSuffix) {
}
