package com.sb24.asanzeman.notification.domain.port.in;

import java.util.List;
import java.util.UUID;

/**
 * One SMS to one recipient (6.4).
 *
 * @param mobile    the number as saved with the request, {@code 09xxxxxxxxx}
 * @param template  which text
 * @param args      the template arguments, in order
 * @param requestId the request this message is about, for tracing; may be {@code null}
 */
public record EnqueueSmsCommand(String mobile, SmsTemplate template, List<String> args, UUID requestId) {

    public EnqueueSmsCommand {
        args = args == null ? List.of() : List.copyOf(args);
    }
}
