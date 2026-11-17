/**
 * Integration: thin clients for the bank's services (AAA, Yaghut, gateway token, the 9 inquiries,
 * personnelInfo, Notifier, ARcaptcha) plus their in-app fakes. Modules reach these only from their
 * own {@code infrastructure/external} adapters, which implement the module's {@code port/out}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Integration: bank service clients",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.sb24.asanzeman.integration;
