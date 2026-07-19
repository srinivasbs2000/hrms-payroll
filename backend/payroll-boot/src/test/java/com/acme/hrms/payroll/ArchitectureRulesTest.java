package com.acme.hrms.payroll;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {
  private static final List<String> MODULES = List.of(
      "platform", "security", "integrations", "organisation", "compensation", "employeepayroll",
      "payrolloperations", "calculation", "documentsreporting");
  private static JavaClasses classesUnderTest;

  @BeforeAll
  static void importClasses() {
    classesUnderTest = new ClassFileImporter().importPackages("com.acme.hrms.payroll");
  }

  @Test
  void modulesAreFreeOfCycles() {
    slices().matching("com.acme.hrms.payroll.(*)..")
        .should().beFreeOfCycles().check(classesUnderTest);
  }

  @Test
  void moduleInternalsCannotBeImportedFromAnotherModule() {
    for (String module : MODULES) {
      noClasses().that().resideOutsideOfPackage("com.acme.hrms.payroll." + module + "..")
          .should().dependOnClassesThat().resideInAnyPackage("com.acme.hrms.payroll." + module + ".internal..")
          .allowEmptyShould(true).check(classesUnderTest);
    }
  }

  @Test
  void repositoriesRemainInfrastructureOwned() {
    classes().that().haveSimpleNameEndingWith("Repository")
        .should().resideInAnyPackage("..infrastructure..")
        .allowEmptyShould(true).check(classesUnderTest);
    noClasses().that().resideOutsideOfPackage("..infrastructure..")
        .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
        .allowEmptyShould(true).check(classesUnderTest);
  }
}
