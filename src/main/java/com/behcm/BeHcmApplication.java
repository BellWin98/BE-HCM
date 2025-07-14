package com.behcm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class BeHcmApplication {

    public static void main(String[] args) {
        SpringApplication.run(BeHcmApplication.class, args);
    }

}
