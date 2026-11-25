/**
 * Shared kernel: framework-free helpers (NationalCode, JalaliDate, Money) and the cross-cutting
 * web pieces (RFC 7807 errors, correlation id, security chain, configuration guard).
 */
@ApplicationModule(
        displayName = "Shared kernel",
        type = ApplicationModule.Type.OPEN)
package com.sb24.asanzeman.shared;

import org.springframework.modulith.ApplicationModule;

