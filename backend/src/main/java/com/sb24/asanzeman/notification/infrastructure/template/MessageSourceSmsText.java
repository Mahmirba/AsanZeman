package com.sb24.asanzeman.notification.infrastructure.template;

import com.sb24.asanzeman.notification.domain.port.in.SmsTemplate;
import com.sb24.asanzeman.notification.domain.port.out.SmsTextPort;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/** SMS texts come from {@code messages_fa.properties}, the same file as the error texts (6.4). */
@Component
public class MessageSourceSmsText implements SmsTextPort {

    static final Locale FARSI = Locale.forLanguageTag("fa");

    private final MessageSource messages;

    public MessageSourceSmsText(MessageSource messages) {
        this.messages = messages;
    }

    @Override
    public String render(SmsTemplate template, List<String> args) {
        return messages.getMessage(template.messageKey(), args.toArray(), FARSI);
    }
}
