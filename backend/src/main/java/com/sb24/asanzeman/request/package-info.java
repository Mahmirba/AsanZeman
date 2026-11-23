/**
 * Request: registration, customer completion, collateral, branch review, lock, cancel/revoke, branches
 */
@org.springframework.modulith.ApplicationModule(displayName = "Request: registration, customer completion, collateral, branch review, lock, cancel/revoke, branches", allowedDependencies = {"integration", "auth :: port-in", "auth :: model", "inquiry :: port-in", "inquiry :: model", "notification :: port-in"})
package com.sb24.asanzeman.request;
