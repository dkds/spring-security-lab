package com.dkds.resourceserver;

import com.dkds.commonsecurity.ResourceServerSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/// This app's entire security posture (STATELESS JWT resource server, no
/// form login, no session) comes from the shared common-security module —
/// see ResourceServerSecurityConfig for why that's an @Import here rather
/// than something built locally.
@SpringBootApplication
@Import(ResourceServerSecurityConfig.class)
public class ResourceServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ResourceServerApplication.class, args);
	}

}
