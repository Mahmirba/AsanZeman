package com.sb24.asanzeman.auth.infrastructure.web;

import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.port.in.AgencyLoginUseCase;
import com.sb24.asanzeman.auth.domain.port.in.BankLoginUseCase;
import com.sb24.asanzeman.auth.domain.port.in.LogoutUseCase;
import com.sb24.asanzeman.auth.domain.port.in.RefreshSessionUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, refresh and logout (6, D7). No cookies: tokens travel in JSON bodies and the {@code Authorization} header.
 * Controllers inject the use-case ports only (D49).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final BankLoginUseCase bankLogin;
    private final AgencyLoginUseCase agencyLogin;
    private final RefreshSessionUseCase refresh;
    private final LogoutUseCase logout;
    private final Clock clock;

    public AuthController(BankLoginUseCase bankLogin, AgencyLoginUseCase agencyLogin, RefreshSessionUseCase refresh,
            LogoutUseCase logout, Clock clock) {
        this.bankLogin = bankLogin;
        this.agencyLogin = agencyLogin;
        this.refresh = refresh;
        this.logout = logout;
        this.clock = clock;
    }

    @PostMapping("/bank/login")
    public TokenResponse bankLogin(@Valid @RequestBody BankLoginRequest body, HttpServletRequest request) {
        return TokenResponse.of(
                bankLogin.login(body.username(), body.password(), body.arcaptchaToken(), client(request)), clock);
    }

    @PostMapping("/agency/login")
    public TokenResponse agencyLogin(@Valid @RequestBody AgencyLoginRequest body, HttpServletRequest request) {
        return TokenResponse.of(
                agencyLogin.login(body.username(), body.password(), body.arcaptchaToken(), client(request)), clock);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
        return TokenResponse.of(refresh.refresh(body.refreshToken(), client(request)), clock);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) LogoutRequest body,
            HttpServletRequest request) {
        logout.logout(jwt.getId(), jwt.getExpiresAt(), jwt.getSubject(), body == null ? null : body.refreshToken(),
                client(request));
    }

    private static ClientInfo client(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
