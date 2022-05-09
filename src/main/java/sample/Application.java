package sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// We use Spring Gateway to setup an embedded reverse proxy to PAS.
// This allows the viewer to make requests to PAS through this web application.
//
// The reverse proxy route which the viewer will use is configured in application.yml.
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
