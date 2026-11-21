package com.sb24.asanzeman.notification.infrastructure.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.notification.domain.port.in.SmsTemplate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** Uses the real {@code messages_fa.properties}, so a missing or broken template fails here. */
class MessageSourceSmsTextTest {

    private final MessageSourceSmsText texts = new MessageSourceSmsText(messages());

    private static ResourceBundleMessageSource messages() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Test
    void theRegistrationTextNamesTheAgencyAndGivesTheLink() {
        String text = texts.render(SmsTemplate.REQUEST_REGISTERED, List.of("Sky Travel"));

        assertThat(text).startsWith("مشتری گرامی!").contains("آژانس هواپیمایی Sky Travel")
                .endsWith("www.asanzeman.sb24.com");
        assertThat(text).contains("\n");
    }

    @Test
    void theCancellationTextNamesTheAgencyAndEndsWithTheDate() {
        String text = texts.render(SmsTemplate.REQUEST_CANCELLED, List.of("Sky Travel", "1405/08/30"));

        assertThat(text).startsWith("بانک سامان\nمشتری گرامی\n").contains("آژانس هواپیمایی Sky Travel")
                .contains("لغو شد").endsWith("\n1405/08/30");
    }

    @Test
    void everyTemplateHasATextInTheMessageConfig() {
        for (SmsTemplate template : SmsTemplate.values()) {
            assertThat(texts.render(template, List.of("x", "y", "z"))).isNotBlank();
        }
    }
}
