/**
 * Scheduler: leader-elected jobs
 */
@ApplicationModule(displayName = "Scheduler: leader-elected jobs", allowedDependencies = {"request :: port-in", "notification :: port-in", "auth :: port-in"})
package com.sb24.asanzeman.scheduler;

import org.springframework.modulith.ApplicationModule;

