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
/// warrants one. Per DESIGN.md: positioned before
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
/// itself feed back into CaptchaService's own failure count.
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
        if (captchaService.isRequired(username, request.getRemoteAddr())
                && !captchaService.verify(request.getParameter("captchaToken"))) {
            response.sendRedirect(SecurityConstants.LOGIN_PAGE + "?captcha");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
