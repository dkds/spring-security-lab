package com.example.controller;

import com.example.dto.ErrorResponseDto;
import com.example.dto.SSOInitiateRequestDto;
import com.example.dto.SSOInitiateResponseDto;
import com.example.dto.SSOType;
import com.example.service.AuthProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@Controller
@RequestMapping
public class SSOController {

    private static final String UI_LOCATION = "http://localhost:3000";
    public static final String ENDPOINT_AUTHENTICATE_SAML = "/saml2/authenticate/";
    private final AuthProviderService authProviderService;

    @PostMapping("sso/initiate")
    public ResponseEntity<?> ssoInitiate(@RequestBody SSOInitiateRequestDto request) {
        try {
            var authProviderDto = authProviderService.getProviderDetailsForSSO(request);
            if (SSOType.SAML.equals(authProviderDto.type())) {
                var redirectLocation = ENDPOINT_AUTHENTICATE_SAML + URLEncoder.encode(request.providerName(), StandardCharsets.UTF_8);
                return ResponseEntity.ok(new SSOInitiateResponseDto(redirectLocation));
            }
            return ResponseEntity.badRequest().body(new ErrorResponseDto("Unknown SSO type. Only SAML is supported at this time."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto(e.getMessage()));
        }
    }

    @GetMapping("login")
    public RedirectView loginRedirect(@RequestParam(required = false) String logout) {
        if (logout != null) {
            return new RedirectView(UI_LOCATION + "/login");
        }
        return new RedirectView(UI_LOCATION);
    }
}
