package com.shopifyplus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Exclude the default in-memory user auto-config — we authenticate via JWT, so the
// generated dev password (and its warning) is unused noise.
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableScheduling
public class ShopifyPlusApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopifyPlusApplication.class, args);
    }
}
