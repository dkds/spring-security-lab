package com.dkds.authserver.login;

import com.dkds.authserver.onetimetoken.EmailOttDeliveryHandler;
import com.dkds.authserver.security.SecurityConstants;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/// Login page controller.
/// Serves the form login page and the OTT code entry screen.
@Controller
public class LoginController {

    @GetMapping(SecurityConstants.LOGIN_PAGE)
    public String loginPage() {
        return "login";
    }

    /// OTT code entry screen — shown after the 6-digit code has been emailed.
    /// Template: templates/ott-input.html
    @GetMapping(EmailOttDeliveryHandler.OTT_INPUT_URL)
    public String ottInputPage() {
        return "ott-input";
    }
}
