package com.booking.azure.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class HexagonalArchitectureTest {

    private JavaClasses classes;

    @BeforeClass
    public void setup() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.booking.azure");
    }

    @Test(description = "Domain layer should not depend on Application or Infrastructure layers")
    public void domainLayerDependencies() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
                .check(classes);
    }

    @Test(description = "Application layer should not depend on Infrastructure layer")
    public void applicationLayerDependencies() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(classes);
    }

    @Test(description = "Inbound Adapters should not directly depend on Outbound Adapters")
    public void adapterDependencies() {
        noClasses()
                .that().resideInAPackage("..infrastructure.adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure.adapter.out..")
                .check(classes);
    }

    /**
     * The domain must be runnable without a container.
     *
     * A domain that reaches for {@code @Service} or {@code @Transactional} can
     * only be exercised by starting Spring, which is why the booking rules were
     * previously unreachable from a plain unit test.
     */
    @Test(description = "Domain layer should not depend on Spring")
    public void domainIsFreeOfSpring() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .check(classes);
    }

    /**
     * Persistence is a storage decision, not a modelling one.
     *
     * The aggregate must not learn about tables, lazy loading or entity
     * managers; that knowledge belongs to the adapter that maps it.
     */
    @Test(description = "Domain layer should not depend on JPA or any persistence API")
    public void domainIsFreeOfPersistence() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "org.hibernate..")
                .check(classes);
    }

    /**
     * The domain must not define the wire format.
     *
     * When an aggregate carries {@code @JsonProperty}, the shape of an external
     * API dictates the shape of the model, and renaming a field becomes a
     * breaking change to a third party's contract.
     */
    @Test(description = "Domain model, ports and events should not depend on Jackson")
    public void domainModelIsFreeOfJackson() {
        noClasses()
                .that().resideInAnyPackage("..domain.model..", "..domain.event..", "..domain.port..")
                .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson..")
                .check(classes);
    }

    /**
     * Validation annotations are a presentation concern: they describe what an
     * incoming HTTP body must look like, not what the model guarantees. The
     * model enforces its own invariants in constructors.
     */
    @Test(description = "Domain model, ports and events should not depend on Bean Validation")
    public void domainModelIsFreeOfBeanValidation() {
        noClasses()
                .that().resideInAnyPackage("..domain.model..", "..domain.event..", "..domain.port..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.validation..")
                .check(classes);
    }
}
