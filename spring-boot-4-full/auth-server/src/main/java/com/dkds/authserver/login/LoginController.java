package com.dkds.authserver.login;

import com.dkds.authserver.security.SecurityConstants;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/// Login page controller.
/// Serves the form login page for users.
@Controller
public class LoginController {

    @GetMapping(SecurityConstants.LOGIN_PAGE)
    public String loginPage() {
        return "login";
    }
}
