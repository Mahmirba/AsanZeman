package com.sb24.asanzeman.request.domain.port.in;

import java.util.UUID;

/** The admin making a change, as the audit log records it; taken from the access token and the connection. */
public record AdminActor(UUID id, String name, String clientIp, String userAgent) {
}
