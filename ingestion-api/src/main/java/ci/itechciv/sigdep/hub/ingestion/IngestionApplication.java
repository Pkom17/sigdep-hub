package ci.itechciv.sigdep.hub.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "ci.itechciv.sigdep.hub.ingestion",
        "ci.itechciv.sigdep.hub.domain"
})
public class IngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionApplication.class, args);
    }
}
