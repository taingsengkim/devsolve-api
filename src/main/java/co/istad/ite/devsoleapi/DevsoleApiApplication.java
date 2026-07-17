package co.istad.ite.devsoleapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class DevsoleApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevsoleApiApplication.class, args);
    }

}
