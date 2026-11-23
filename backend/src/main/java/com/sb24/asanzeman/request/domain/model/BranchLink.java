package com.sb24.asanzeman.request.domain.model;

/** A branch with the one that handles it now: itself when it is open, another branch when it was closed (4.4). */
public record BranchLink(String code, boolean active, String parent) {
}
