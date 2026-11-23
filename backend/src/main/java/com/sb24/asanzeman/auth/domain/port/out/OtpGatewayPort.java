package com.sb24.asanzeman.auth.domain.port.out;

/** The SMS centre's one-time code (D34): it makes, sends and checks the code. */
public interface OtpGatewayPort {

    /**
     * @param mobile {@code 09xxxxxxxxx}
     * @return the id that {@link #validate} needs
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException when the SMS centre cannot send it
     */
    String send(String mobile);

    /** @return {@code false} for a wrong or used code */
    boolean validate(String mobile, String otpId, String code);
}
