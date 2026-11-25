package com.sb24.asanzeman.auth.domain.model;

import java.util.UUID;

/**
 * Who a session is for and what they may do (6): the bank user's or agency's id and name, the role, and the branch for
 * bank users. The branch is a snapshot taken at login and stays fixed for the whole session (D33).
 *
 * @param subjectId   id of the bank user or agency
 * @param subjectName the {@code accountName} (D33) or the agency name; shown to others, e.g. as the lock holder
 * @param role        the session's role
 * @param branchCode  the user's branch, {@code null} for roles without one
 * @param actor       for an agency, the Yaghut user who logged in for it, so the audit log names the person and not only
 *                    the agency; {@code null} for the other roles
 */
public record Session(UUID subjectId, String subjectName, Role role, String branchCode, String actor) {

    public Session {
        if (subjectId == null || subjectName == null || subjectName.isBlank() || role == null) {
            throw new IllegalArgumentException("a session needs a subject id, a name and a role");
        }
    }

    public Session(UUID subjectId, String subjectName, Role role, String branchCode) {
        this(subjectId, subjectName, role, branchCode, null);
    }
}
