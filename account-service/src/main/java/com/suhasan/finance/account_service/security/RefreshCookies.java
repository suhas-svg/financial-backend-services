package com.suhasan.finance.account_service.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * The refresh-token cookie: httpOnly so scripts cannot read it, SameSite=Strict so
 * other sites cannot make the browser send it, and scoped to the auth endpoints so
 * it never accompanies ordinary API calls.
 */
@Component
public class RefreshCookies {

    private final String name;
    private final String path;
    private final boolean secure;
    private final Clock clock = Clock.systemUTC();

    public RefreshCookies(
            @Value("${security.refresh-token.cookie-name:fc_refresh}") final String name,
            @Value("${security.refresh-token.cookie-path:/account-api/api/auth}") final String path,
            @Value("${security.refresh-token.cookie-secure:true}") final boolean secure) {
        this.name = name;
        this.path = path;
        this.secure = secure;
    }

    public Optional<String> read(final HttpServletRequest request) {
        final Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public String issue(final RefreshTokenService.IssuedToken token) {
        final Duration maxAge = Duration.between(clock.instant(), token.expiresAt());
        return base(token.value()).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build().toString();
    }

    public String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(final String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(path);
    }
}
