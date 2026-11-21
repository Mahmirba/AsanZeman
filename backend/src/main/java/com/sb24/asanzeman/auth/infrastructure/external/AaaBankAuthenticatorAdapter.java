package com.sb24.asanzeman.auth.infrastructure.external;

import com.sb24.asanzeman.auth.domain.model.BankAuthentication;
import com.sb24.asanzeman.auth.domain.model.BankRole;
import com.sb24.asanzeman.auth.domain.port.out.BankAuthenticatorPort;
import com.sb24.asanzeman.integration.aaa.AaaClient;
import com.sb24.asanzeman.integration.aaa.AaaRoleResult;
import com.sb24.asanzeman.integration.aaa.config.AaaProperties;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 6.2: any {@code DetailError} means AAA refused the user name or password; its code, description and inner exception
 * are logged in full (D22). Otherwise the AAA role names are mapped with {@code azs.integration.aaa.role-map} (default
 * {@code Branch -> BRANCH}, {@code Admin -> ADMIN}); any other role is ignored.
 */
@Component
public class AaaBankAuthenticatorAdapter implements BankAuthenticatorPort {

    private static final Logger log = LoggerFactory.getLogger(AaaBankAuthenticatorAdapter.class);

    private final AaaClient client;
    private final AaaProperties properties;

    public AaaBankAuthenticatorAdapter(AaaClient client, AaaProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public BankAuthentication authenticate(String userName, String password) {
        AaaRoleResult result = client.getRoleWindows(userName, password);
        if (!result.errors().isEmpty()) {
            result.errors().forEach(e -> log.info("AAA refused the login of {}: code {}, {} ({})", userName, e.code(),
                    e.description(), e.innerException()));
            return BankAuthentication.rejected();
        }
        Set<BankRole> roles = EnumSet.noneOf(BankRole.class);
        for (String aaaRole : result.roles()) {
            String mapped = properties.roleMap().get(aaaRole);
            if (mapped != null) {
                toRole(aaaRole, mapped).ifPresent(roles::add);
            }
        }
        return BankAuthentication.accepted(roles);
    }

    /** A role-map value that is not an Asan Zeman role is a configuration mistake: alert ops, do not fail the login. */
    private static Optional<BankRole> toRole(String aaaRole, String mapped) {
        try {
            return Optional.of(BankRole.valueOf(mapped.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            log.error("azs.integration.aaa.role-map.{}={} is not an Asan Zeman role (BRANCH or ADMIN): ignored",
                    aaaRole, mapped);
            return Optional.empty();
        }
    }
}
