package com.tutorcraft.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ARCH-01/02: границы модулей и направление зависимостей слоёв. */
class ModuleBoundariesTest {

    private static final String ROOT = "com.tutorcraft.core";
    private static final List<String> MODULES = List.of("identity", "org", "access", "audit", "files", "courses",
            "enrollment", "assessment", "gradebook", "progress", "communication", "dashboard", "billing", "integrations",
            "activity");
    private static final List<String> INTERNAL_LAYERS = List.of("application", "infrastructure", "web");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ROOT);
    }

    @Test
    void modulesDoNotReachIntoOtherModulesInternals() {
        for (String module : MODULES) {
            for (String other : MODULES) {
                if (module.equals(other)) {
                    continue;
                }
                for (String layer : INTERNAL_LAYERS) {
                    rule(module, other, layer).check(classes);
                }
            }
        }
    }

    @Test
    void domainIsFreeOfFrameworks() {
        noClasses().that().resideInAPackage(ROOT + "..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.servlet..", "org.bson..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void controllersDoNotUseInfrastructure() {
        noClasses().that().resideInAPackage(ROOT + "..web..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + "..infrastructure..")
                .allowEmptyShould(true)
                .check(classes);
    }

    private static ArchRule rule(String module, String other, String layer) {
        return noClasses().that().resideInAPackage(ROOT + "." + module + "..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + "." + other + "." + layer + "..")
                .allowEmptyShould(true)
                .because("module " + module + " must use only the public API of " + other);
    }
}
