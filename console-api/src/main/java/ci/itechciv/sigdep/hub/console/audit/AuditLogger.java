package ci.itechciv.sigdep.hub.console.audit;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    @Pointcut("@annotation(ci.itechciv.sigdep.hub.console.audit.AuditedAccess)")
    public void auditedAccess() {}

    @AfterReturning("auditedAccess()")
    public void logAccess() {
        // TODO: persist to audit.access_log with user_id, action, entity_type, entity_id, ip, etc.
        log.debug("Audited access — wire persistence");
    }
}
