package com.sb24.asanzeman.request.domain.port.in;

import java.util.UUID;

/** The agency's «ثبت درخواست ابطال» action on one of its requests (4.2). */
public record CancelRequestCommand(UUID agencyId, String agencyName, UUID requestId, String clientIp,
        String userAgent) {
}
