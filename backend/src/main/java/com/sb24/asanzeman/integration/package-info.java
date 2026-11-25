/**
 * Integration: thin clients for the bank's services (AAA, Yaghut, gateway token, the 9 inquiries,
 * personnelInfo, Notifier, ARcaptcha) plus their in-app fakes. Modules reach these only from their
 * own {@code infrastructure/external} adapters, which implement the module's {@code port/out}.
 */
@ApplicationModule(
        displayName = "Integration: bank service clients",
        type = ApplicationModule.Type.OPEN)
package com.sb24.asanzeman.integration;

import org.springframework.modulith.ApplicationModule;

