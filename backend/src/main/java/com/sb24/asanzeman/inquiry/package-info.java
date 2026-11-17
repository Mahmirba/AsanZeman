/**
 * Inquiry: the two-stage inquiry chain (StageRunner). It never calls the request module back:
 * results are published as application events that the request module listens to (no cycle).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Inquiry: the two-stage inquiry chain (StageRunner)", allowedDependencies = {"integration"})
package com.sb24.asanzeman.inquiry;
