package ci.itechciv.sigdep.hub.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "ci.itechciv.sigdep.hub.ingestion",
        "ci.itechciv.sigdep.hub.domain"
})
@EntityScan("ci.itechciv.sigdep.hub.domain.entity")
@EnableJpaRepositories("ci.itechciv.sigdep.hub.domain.repository")
public class IngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionApplication.class, args);
    }
}
