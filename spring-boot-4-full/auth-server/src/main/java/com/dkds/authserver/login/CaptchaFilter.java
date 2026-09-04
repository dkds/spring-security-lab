package com.dkds.authserver.login;

import com.dkds.authserver.security.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/// Gates a password-login attempt behind a captcha challenge when
/// CaptchaService says the recent failure history for this username+IP
/// warrants one, and escalates repeated wrong captcha submissions into a
/// lockout. Per DESIGN.md: positioned before
/// UsernamePasswordAuthenticationFilter, inside FormLoginConfigurer only —
/// never wired onto Chain 1 or Chain 2 (see Phase9ChainInventoryTests).
///
/// Deliberately self-scoped to POST /login (the exact URL
/// UsernamePasswordAuthenticationFilter itself processes) rather than
/// relying solely on chain placement — a filter this early in the chain
/// would otherwise run on every request Chain 3 sees, not just login
/// attempts.
///
/// Rejecting here (missing/invalid captcha) never reaches
/// UsernamePasswordAuthenticationFilter at all, so it's not recorded as a
/// login_attempt (no username/password was actually checked) and doesn't
/// itself feed back into CaptchaService's own credential-failure count.
///
/// A missing captcha token (the informational bounce a caller gets the
/// FIRST time captcha becomes required, before the form has even rendered
/// the field) is deliberately NOT counted as a wrong submission — only a
/// token that was actually present and failed verify(...) counts toward the
/// lockout. Otherwise the very redirect that asks for a captcha would count
/// against the caller.
@RequiredArgsConstructor
public class CaptchaFilter extends OncePerRequestFilter {

    private final RequestMatcher loginRequestMatcher =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, SecurityConstants.LOGIN_PROCESSING_URL);

    private final CaptchaService captchaService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!loginRequestMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = request.getParameter("username");

        // A UX nicety, not the enforcement point — AppUserDetailsService
        // rejects a locked account independently regardless of this check
        // (correct password and valid captcha included). Checked here only
        // so a locked caller gets a specific message instead of a generic
        // one after filling in the form again.
        if (captchaService.isLocked(username)) {
            response.sendRedirect(SecurityConstants.LOGIN_PAGE + "?locked");
            return;
        }

        if (captchaService.isRequired(username, request.getRemoteAddr())) {
            String token = request.getParameter("captchaToken");
            if (token == null || token.isBlank()) {
                response.sendRedirect(SecurityConstants.LOGIN_PAGE + "?captcha");
                return;
            }
            if (!captchaService.verify(token)) {
                captchaService.recordCaptchaFailure(username);
                response.sendRedirect(SecurityConstants.LOGIN_PAGE + "?captcha&error");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
