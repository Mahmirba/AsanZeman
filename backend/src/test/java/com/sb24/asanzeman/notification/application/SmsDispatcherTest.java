package com.sb24.asanzeman.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.notification.domain.model.OutboxSms;
import com.sb24.asanzeman.notification.domain.model.SmsCredentialsException;
import com.sb24.asanzeman.notification.domain.model.SmsRejectedException;
import com.sb24.asanzeman.notification.domain.port.out.SmsGatewayPort;
import com.sb24.asanzeman.notification.domain.port.out.SmsOutboxPort;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SmsDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final SmsSettings SETTINGS = new SmsSettings(20, Duration.ofMinutes(2), Duration.ofSeconds(30),
            Duration.ofHours(1), Duration.ofMinutes(15));

    @Mock
    private SmsOutboxPort outbox;
    @Mock
    private SmsGatewayPort gateway;

    private SmsDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SmsDispatcher(outbox, gateway, new MutableClock(NOW), SETTINGS);
    }

    private static OutboxSms sms(int attempts) {
        return new OutboxSms(UUID.randomUUID(), "09121234567", "REQUEST_REGISTERED", "the text", UUID.randomUUID(),
                attempts);
    }

    private void due(OutboxSms... messages) {
        when(outbox.claimDue(NOW, 20, Duration.ofMinutes(2))).thenReturn(List.of(messages));
    }

    @Test
    void nothingDueMeansNothingSent() {
        due();

        assertThat(dispatcher.dispatchDue()).isZero();
        verify(gateway, never()).send(anyString(), anyString());
    }

    @Test
    void sendsToTheGatewayNumberAndMarksItSentWithTheSmsId() {
        OutboxSms one = sms(1);
        due(one);
        when(gateway.send("0989121234567", "the text")).thenReturn("SMS-1");

        assertThat(dispatcher.dispatchDue()).isEqualTo(1);

        verify(outbox).markSent(one.id(), "SMS-1", NOW);
    }

    @Test
    void a400GoesToTheDeadLetterListAndIsNotRetried() {
        OutboxSms one = sms(1);
        due(one);
        when(gateway.send(anyString(), anyString())).thenThrow(new SmsRejectedException("bad number"));

        dispatcher.dispatchDue();

        verify(outbox).markDead(one.id(), "bad number", NOW);
        verify(outbox, never()).markRetry(any(), any(), any());
    }

    @Test
    void a403KeepsTheMessageWithTheLongBackoff() {
        OutboxSms one = sms(1);
        due(one);
        when(gateway.send(anyString(), anyString())).thenThrow(new SmsCredentialsException("contact support"));

        dispatcher.dispatchDue();

        verify(outbox).markRetry(one.id(), NOW.plus(Duration.ofMinutes(15)), "contact support");
        verify(outbox, never()).markDead(any(), any(), any());
    }

    @Test
    void aTechnicalFailureIsRetriedWithABackoffThatGrowsWithTheAttempts() {
        OutboxSms first = sms(1);
        OutboxSms third = sms(3);
        due(first, third);
        when(gateway.send(anyString(), anyString())).thenThrow(
                new TemporarilyUnavailableException("Notifier", new RuntimeException("timeout")));

        dispatcher.dispatchDue();

        verify(outbox).markRetry(eq(first.id()), eq(NOW.plusSeconds(30)), anyString());
        verify(outbox).markRetry(eq(third.id()), eq(NOW.plusSeconds(120)), anyString());
    }

    @Test
    void aFailureOnOneMessageNeverStopsTheOthers() {
        OutboxSms bad = sms(1);
        OutboxSms good = sms(1);
        due(bad, good);
        when(gateway.send(anyString(), anyString())).thenThrow(new SmsRejectedException("bad")).thenReturn("SMS-2");

        assertThat(dispatcher.dispatchDue()).isEqualTo(2);

        verify(outbox).markDead(bad.id(), "bad", NOW);
        verify(outbox).markSent(good.id(), "SMS-2", NOW);
    }

    @Test
    void aLongErrorIsCutToTheColumnSize() {
        OutboxSms one = sms(1);
        due(one);
        when(gateway.send(anyString(), anyString())).thenThrow(new SmsRejectedException("x".repeat(900)));

        dispatcher.dispatchDue();

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(outbox).markDead(eq(one.id()), error.capture(), eq(NOW));
        assertThat(error.getValue()).hasSize(500);
    }

    @Test
    void anErrorWithoutAMessageIsStoredAsNull() {
        OutboxSms one = sms(1);
        due(one);
        when(gateway.send(anyString(), anyString())).thenThrow(new SmsRejectedException(null));

        dispatcher.dispatchDue();

        verify(outbox).markDead(one.id(), null, NOW);
    }
}
