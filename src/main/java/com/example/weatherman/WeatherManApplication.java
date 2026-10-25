package com.example.weatherman;

import com.example.weatherman.config.WeatherManProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WeatherManProperties.class)
public class WeatherManApplication {
    public static void main(String[] args) {
        SpringApplication.run(WeatherManApplication.class, args);
    }
}
