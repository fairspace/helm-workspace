package io.fairspace.saturn.audit;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import static io.fairspace.saturn.auth.RequestContext.getClaims;

public class Audit {
    private static final Logger log = LogManager.getLogger("audit");

    public static void audit(String event, Object... params) {
        ThreadContext.put("event", event);

        for (var i = 0; i < params.length / 2; i++) {
            if (params[2 * i + 1] != null) {
                ThreadContext.put((String) params[2 * i], Objects.toString(params[2 * i + 1]));
            }
        }

        var claims = getClaims();
        String preferredUsername = claims.getPreferredUsername();

        if (preferredUsername != null) {
            ThreadContext.put("user_name", preferredUsername);
        }

        String email = claims.getEmail();
        if (email != null) {
            ThreadContext.put("user_email", email);
        }
        ThreadContext.put("user_id", claims.getSubject());

        log.trace(event);

        ThreadContext.clearAll();
    }
}
