package com.sb24.asanzeman.request.domain.service;

import com.sb24.asanzeman.request.domain.model.BranchLink;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The branch table checks of 4.4. A replacement is set only by the DBA script, which keeps every branch pointing straight
 * at an open branch, so anything found here means a script was edited by hand or a branch closed later:
 * <ul>
 *   <li>a cycle: following the parents leads back to the branch;</li>
 *   <li>a chain: the parent is itself closed, so it should point at its own replacement;</li>
 *   <li>a parent that is inactive;</li>
 *   <li>an inactive branch with no replacement (it points at itself).</li>
 * </ul>
 */
public final class BranchIntegrity {

    private BranchIntegrity() {
    }

    /** @return one line for ops per problem, empty when the table is sound */
    public static List<String> problems(List<BranchLink> branches) {
        Map<String, BranchLink> byCode = new HashMap<>();
        branches.forEach(b -> byCode.put(b.code(), b));
        List<String> problems = new ArrayList<>();
        for (BranchLink b : branches) {
            if (b.parent().equals(b.code())) {
                if (!b.active()) {
                    problems.add("شعبه " + b.code() + " بسته شده اما شعبه جایگزین ندارد");
                }
                continue;
            }
            BranchLink parent = byCode.get(b.parent());
            if (parent == null) {
                problems.add("branch " + b.code() + " points to " + b.parent() + ", which is not a branch");
            } else if (leadsBackTo(b, byCode)) {
                problems.add("branch " + b.code() + " is in a cycle of replacements");
            } else if (!parent.parent().equals(parent.code())) {
                problems.add("branch " + b.code() + " points to " + parent.code() + ", which was itself replaced by "
                        + parent.parent() + " (a chain)");
            } else if (!parent.active()) {
                problems.add("branch " + b.code() + " points to " + parent.code() + ", which is inactive");
            }
        }
        return problems;
    }

    private static boolean leadsBackTo(BranchLink start, Map<String, BranchLink> byCode) {
        BranchLink current = byCode.get(start.parent());
        for (int steps = 0; current != null && steps < byCode.size(); steps++) {
            if (current.code().equals(start.code())) {
                return true;
            }
            current = byCode.get(current.parent());
        }
        return false;
    }
}
