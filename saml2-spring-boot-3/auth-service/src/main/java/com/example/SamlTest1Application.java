package com.example;

import com.example.config.ConfigProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


@SpringBootApplication
@EnableConfigurationProperties(ConfigProperties.class)
public class SamlTest1Application {

    public static void main(String[] args) {
        SpringApplication.run(SamlTest1Application.class, args);
    }

}
