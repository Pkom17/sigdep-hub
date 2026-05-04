package ci.itechciv.sigdep.hub.console;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "ci.itechciv.sigdep.hub.console",
        "ci.itechciv.sigdep.hub.domain"
})
@EntityScan("ci.itechciv.sigdep.hub.domain.entity")
@EnableJpaRepositories("ci.itechciv.sigdep.hub.domain.repository")
@EnableCaching
public class ConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsoleApplication.class, args);
    }
}
