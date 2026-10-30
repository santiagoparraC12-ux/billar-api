package com.billar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.ZoneId;

@SpringBootApplication
public class BillarApplication {
    public static void main(String[] args) {
        SpringApplication.run(BillarApplication.class, args);
    }

    @Bean
    ZoneId businessZone(@Value("${billar.timezone}") String tz) {
        return ZoneId.of(tz);
    }
}
