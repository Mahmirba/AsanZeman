package com.sb24.asanzeman.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ADR-017 / D49: the hexagon inside every module.
 * <ul>
 *   <li>{@code domain} depends only on {@code domain} packages, {@code shared} helpers and the JDK;</li>
 *   <li>{@code application} never imports {@code infrastructure};</li>
 *   <li>Spring Data repositories are used only inside {@code infrastructure.persistence};</li>
 *   <li>{@code integration} clients are used only from {@code infrastructure.external} adapters.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.sb24.asanzeman", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String INFRASTRUCTURE = "..infrastructure..";

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.transaction..",
                    "org.hibernate..", "tools.jackson..", "com.fasterxml.jackson..",
                    "io.github.resilience4j..", "org.springdoc..")
            .as("domain packages must not depend on Spring, JPA, Jackson or other frameworks");

    @ArchTest
    static final ArchRule domainDependsOnlyOnDomainAndShared = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    DOMAIN, "com.sb24.asanzeman.shared.model..", "java..", "javax..", "jakarta.validation..")
            .as("domain may use other domain packages, shared.model helpers and the JDK only");

    @ArchTest
    static final ArchRule applicationDoesNotSeeInfrastructure = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
            .as("use cases depend on ports, never on adapters");

    @ArchTest
    static final ArchRule springDataOnlyInPersistence = noClasses()
            .that().resideOutsideOfPackage("..infrastructure.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data.jpa..", "org.springframework.data.repository..")
            .as("Spring Data repositories are an infrastructure detail (D49)");

    @ArchTest
    static final ArchRule integrationClientsOnlyFromExternalAdapters = noClasses()
            .that().resideInAnyPackage(DOMAIN, APPLICATION, "..infrastructure.web..", "..infrastructure.persistence..")
            .should().dependOnClassesThat().resideInAPackage("com.sb24.asanzeman.integration..")
            .as("bank service clients are reached only through infrastructure.external adapters");

    @ArchTest
    static final ArchRule controllersInjectPortsNotServices = noClasses()
            .that().resideInAPackage("..infrastructure.web..")
            .should().dependOnClassesThat().resideInAPackage(APPLICATION)
            .as("controllers inject port/in interfaces, never application classes");

    @ArchTest
    static final ArchRule noJpaAnnotationsOutsidePersistence = noClasses()
            .that().resideOutsideOfPackage("..infrastructure.persistence..")
            .should().beAnnotatedWith(jakarta.persistence.Entity.class)
            .as("JPA entities live in infrastructure.persistence and are mapped to domain models");
}
