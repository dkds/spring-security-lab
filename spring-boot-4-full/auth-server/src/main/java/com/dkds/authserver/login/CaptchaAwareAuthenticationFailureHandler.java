package com.dkds.authserver.login;

import com.dkds.authserver.security.SecurityConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;

/// Wraps the default form-login failure redirect so a wrong-PASSWORD
/// rejection doesn't drop the captcha field, and counts toward the same
/// lockout CaptchaFilter's own wrong-token path uses. This handler only
/// ever runs after CaptchaFilter has already let the request reach
/// UsernamePasswordAuthenticationFilter — which, whenever CaptchaService
/// .isRequired(...) is true, only happens because a non-blank captchaToken
/// was submitted and verified for THIS exact request. Presence of that
/// token is therefore the correct signal here, not a fresh
/// .isRequired(...) call: re-checking would flip true on the very failure
/// that FIRST crosses the threshold (before this request's own CaptchaFilter
/// pass ever required a token), wrongly gating and counting an attempt that
/// never went through the captcha step at all.
///
/// Without the CaptchaService.recordCaptchaFailure(...) call below, an
/// attacker who already has one non-blank token can guess passwords
/// indefinitely once past the gate — this lab's captcha accepts any
/// non-blank value (CaptchaService.verify(...)), so the token itself is
/// trivial to keep resending; only the SAME lockout CaptchaFilter's own
/// wrong-token path enforces actually stops that.
///
/// Deliberately a SEPARATE query parameter ("captchaRequired") from
/// CaptchaFilter's own "captcha" — reusing "captcha" here would make
/// login.html's "Verification Failed" message (captcha+error together)
/// fire for what is actually an ordinary bad-credentials rejection, not a
/// rejected captcha token.
@RequiredArgsConstructor
public class CaptchaAwareAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final CaptchaService captchaService;

    private final AuthenticationFailureHandler plain =
            new SimpleUrlAuthenticationFailureHandler(SecurityConstants.LOGIN_PAGE + "?error");
    private final AuthenticationFailureHandler withCaptchaHint =
            new SimpleUrlAuthenticationFailureHandler(SecurityConstants.LOGIN_PAGE + "?error&captchaRequired");

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        String captchaToken = request.getParameter("captchaToken");
        if (captchaToken != null && !captchaToken.isBlank()) {
            captchaService.recordCaptchaFailure(request.getParameter("username"));
            withCaptchaHint.onAuthenticationFailure(request, response, exception);
        } else {
            plain.onAuthenticationFailure(request, response, exception);
        }
    }
}
