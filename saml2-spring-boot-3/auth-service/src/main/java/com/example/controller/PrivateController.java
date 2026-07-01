package com.example.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PrivateController {

    @GetMapping("/")
    public String getIndex(@AuthenticationPrincipal Saml2AuthenticatedPrincipal principal, Model model) {
        String emailAddress = principal.getFirstAttribute("email");
        model.addAttribute("emailAddress", emailAddress);
        System.out.println("principal: " + principal);
        return "index";
    }

    @GetMapping("/admin")
    public String getUserDetails(@AuthenticationPrincipal Saml2AuthenticatedPrincipal principal, Model model) {
        String emailAddress = principal.getFirstAttribute("email");
        model.addAttribute("emailAddress", emailAddress);
        model.addAttribute("userAttributes", principal.getAttributes());
        System.out.println("principal: " + principal);
        return "admin";
    }
}
