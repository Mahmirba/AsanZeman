package com.sb24.asanzeman.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.notification.domain.model.InvalidMobileException;
import com.sb24.asanzeman.notification.domain.model.OutboxSms;
import com.sb24.asanzeman.notification.domain.port.in.EnqueueSmsCommand;
import com.sb24.asanzeman.notification.domain.port.in.SmsTemplate;
import com.sb24.asanzeman.notification.domain.port.out.SmsOutboxPort;
import com.sb24.asanzeman.notification.domain.port.out.SmsTextPort;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class SmsOutboxServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @Mock
    private SmsTextPort texts;
    @Mock
    private SmsOutboxPort outbox;

    @Test
    void rendersTheTextNowAndQueuesItDueAtOnce() {
        UUID request = UUID.randomUUID();
        when(texts.render(SmsTemplate.REQUEST_REGISTERED, List.of("Sky Travel"))).thenReturn("the text");

        new SmsOutboxService(texts, outbox, new MutableClock(NOW)).enqueue(
                new EnqueueSmsCommand("09121234567", SmsTemplate.REQUEST_REGISTERED, List.of("Sky Travel"), request));

        ArgumentCaptor<OutboxSms> saved = ArgumentCaptor.forClass(OutboxSms.class);
        verify(outbox).insert(saved.capture(), org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(saved.getValue()).extracting(OutboxSms::mobile, OutboxSms::templateCode, OutboxSms::text,
                OutboxSms::requestId, OutboxSms::attempts)
                .containsExactly("09121234567", "REQUEST_REGISTERED", "the text", request, 0);
        assertThat(saved.getValue().id()).isNotNull();
    }

    @Test
    void aBadNumberQueuesNothing() {
        when(texts.render(SmsTemplate.REQUEST_REGISTERED, List.of())).thenReturn("the text");

        assertThatThrownBy(() -> new SmsOutboxService(texts, outbox, new MutableClock(NOW)).enqueue(
                new EnqueueSmsCommand("123", SmsTemplate.REQUEST_REGISTERED, null, null)))
                .isInstanceOf(InvalidMobileException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void itJoinsTheCallersTransactionAndNeverStartsItsOwn() throws Exception {
        var method = SmsOutboxService.class.getMethod("enqueue", EnqueueSmsCommand.class);

        assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void theCommandCopiesItsArguments() {
        var args = new java.util.ArrayList<>(List.of("a"));
        var command = new EnqueueSmsCommand("09121234567", SmsTemplate.REQUEST_REGISTERED, args, null);

        args.clear();

        assertThat(command.args()).containsExactly("a");
        assertThat(new EnqueueSmsCommand("09121234567", SmsTemplate.REQUEST_REGISTERED, null, null).args()).isEmpty();
    }
}
