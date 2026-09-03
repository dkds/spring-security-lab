package com.dkds.authserver.onetimetoken;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;

import java.time.Clock;

/// Infrastructure beans for the OTT feature.
@Configuration
public class OttConfig {

    @Value("${spring.mail.from:noreply@localhost}")
    private String fromAddress;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OneTimeTokenGenerationSuccessHandler ottDeliveryHandler(JavaMailSender mailSender) {
        return new EmailOttDeliveryHandler(mailSender, fromAddress);
    }

    @Bean
    public OttAuthenticationFailureHandler ottAuthenticationFailureHandler(
            OneTimeTokenRepository oneTimeTokenRepository) {
        return new OttAuthenticationFailureHandler(oneTimeTokenRepository);
    }

    @Bean
    public OneTimeTokenConfigurer oneTimeTokenConfigurer(
            NumericOneTimeTokenService numericOneTimeTokenService,
            OneTimeTokenGenerationSuccessHandler ottDeliveryHandler,
            OttAuthenticationFailureHandler ottAuthenticationFailureHandler) {
        return new OneTimeTokenConfigurer(
                numericOneTimeTokenService, ottDeliveryHandler, ottAuthenticationFailureHandler);
    }
}
