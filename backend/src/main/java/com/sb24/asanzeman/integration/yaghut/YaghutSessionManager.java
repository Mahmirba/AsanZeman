package com.sb24.asanzeman.integration.yaghut;

import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.time.Clock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Asan Zeman <b>technical account</b>'s Yaghut session (5.4, D35): one per application instance, in memory. Used
 * for the branch side and {@code BranchSyncJob}. Agency and customer sessions are not managed here: they belong to
 * the user's own Asan Zeman session and cannot be renewed, because their password is never stored.
 *
 * <ul>
 *   <li>The session is reused until it is missing, its {@code sessionExpirationDate} has passed, or Yaghut answers
 *       {@code InvalidSessionWSException}. There is no early refresh.</li>
 *   <li>When several callers need a login at once, only one {@code loginStatic} is sent (a {@link ReentrantLock},
 *       which suits virtual threads).</li>
 *   <li>On {@code InvalidSessionWSException} the session is dropped, a new login is made once and the call is
 *       repeated once.</li>
 *   <li>A refused login of this account is a configuration error: ops is alerted and nothing is retried, so the
 *       account is not locked by repeated wrong passwords.</li>
 * </ul>
 */
public class YaghutSessionManager {

    private static final Logger log = LoggerFactory.getLogger(YaghutSessionManager.class);

    private final YaghutClient client;
    private final String username;
    private final String password;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile YaghutSession session;

    public YaghutSessionManager(YaghutClient client, String username, String password, Clock clock) {
        this.client = client;
        this.username = username;
        this.password = password;
        this.clock = clock;
    }

    /**
     * Runs {@code action} with the technical account's session.
     *
     * @throws TemporarilyUnavailableException when Yaghut is unavailable, the account's login is refused, the
     *         session is refused again right after a fresh login, or access to the service is denied
     * @throws YaghutException for the faults the caller decides on (e.g. {@code InvalidCustomerWSException})
     */
    public <T> T withTechnicalSession(Function<YaghutSession, T> action) {
        YaghutSession used = session();
        try {
            return action.apply(used);
        } catch (YaghutException e) {
            if (e.fault() != YaghutException.Fault.INVALID_SESSION) {
                throw refuse(e);
            }
            discard(used);
        }
        try {
            return action.apply(session());
        } catch (YaghutException e) {
            throw refuse(e);
        }
    }

    /** Faults that are a technical problem for the technical account; every other fault is the caller's. */
    private static RuntimeException refuse(YaghutException e) {
        boolean technical = e.fault() == YaghutException.Fault.INVALID_SESSION
                || e.fault() == YaghutException.Fault.SERVICE_ACCESS_DENIED;
        return technical ? new TemporarilyUnavailableException(YaghutClient.SERVICE, e) : e;
    }

    private YaghutSession session() {
        YaghutSession current = session;
        if (current != null && !current.expired(clock)) {
            return current;
        }
        lock.lock();
        try {
            current = session;
            if (current != null && !current.expired(clock)) {
                return current;
            }
            YaghutSession fresh = login();
            session = fresh;
            return fresh;
        } finally {
            lock.unlock();
        }
    }

    private YaghutSession login() {
        try {
            return client.login(username, password).session();
        } catch (YaghutException e) {
            log.error("Yaghut refused the technical account, check azs.integration.yaghut.technical-user: {}",
                    e.getMessage());
            throw new TemporarilyUnavailableException(YaghutClient.SERVICE, e);
        }
    }

    /** Drops {@code used} only if it is still the current session, so a newer one made by another caller stays. */
    private void discard(YaghutSession used) {
        lock.lock();
        try {
            if (session == used) {
                session = null;
            }
        } finally {
            lock.unlock();
        }
    }
}
