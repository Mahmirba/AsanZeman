package com.sb24.asanzeman.request.domain.model;

/** A branch the agency can choose in the cascading list (SRS UC-AZS-8): its code and name. */
public record BranchSummary(String code, String name, String city) {
}
