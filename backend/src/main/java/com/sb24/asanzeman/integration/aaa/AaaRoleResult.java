package com.sb24.asanzeman.integration.aaa;

import java.util.List;

/**
 * AAA's {@code GetRoleWindowsResult}: {@code DetailError[]} and {@code UserRoles[]} (6.2). A non-empty
 * {@code errors} list means AAA refused the user name or password.
 */
public record AaaRoleResult(List<AaaError> errors, List<String> roles) {

    /** One {@code ResultErrors} entry; all three fields are logged in full when a login is refused (D22). */
    public record AaaError(int code, String description, String innerException) {
    }

    public AaaRoleResult {
        errors = List.copyOf(errors);
        roles = List.copyOf(roles);
    }
}
