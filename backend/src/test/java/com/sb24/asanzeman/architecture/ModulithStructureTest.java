package com.sb24.asanzeman.architecture;

import com.sb24.asanzeman.AsanZemanApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * ADR-001: modules may only use each other's {@code port-in} named interface, and there are no
 * cycles. Fails the build on a violation. Also writes the module docs to target/spring-modulith-docs.
 */
class ModulithStructureTest {

    private final ApplicationModules modules = ApplicationModules.of(AsanZemanApplication.class);

    @Test
    void modulesRespectDeclaredDependenciesAndHaveNoCycles() {
        modules.verify();
    }

    @Test
    void writeModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
