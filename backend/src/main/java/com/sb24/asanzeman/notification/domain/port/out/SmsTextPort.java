package com.sb24.asanzeman.notification.domain.port.out;

import com.sb24.asanzeman.notification.domain.port.in.SmsTemplate;
import java.util.List;

/** Fills an SMS template from the message config (Persian text, 6.4). */
public interface SmsTextPort {

    String render(SmsTemplate template, List<String> args);
}
