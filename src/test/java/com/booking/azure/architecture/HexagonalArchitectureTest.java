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
}
