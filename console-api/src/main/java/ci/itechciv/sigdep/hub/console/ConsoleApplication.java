package ci.itechciv.sigdep.hub.console;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = {
        "ci.itechciv.sigdep.hub.console",
        "ci.itechciv.sigdep.hub.domain"
})
@EnableCaching
public class ConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsoleApplication.class, args);
    }
}
