package com.dkds.authcodetest1.components;

import com.dkds.authcodetest1.entity.AppUser;
import com.dkds.authcodetest1.repositary.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            AppUser appUser = new AppUser();
            appUser.setUsername("testuser");
            appUser.setPassword(passwordEncoder.encode("password"));
            appUser.setRole("USER");
            userRepository.save(appUser);
        }
    }
}