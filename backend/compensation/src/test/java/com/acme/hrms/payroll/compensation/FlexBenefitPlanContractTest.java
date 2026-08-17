package com.acme.hrms.payroll.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitOptionView;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitOptionWriteRequest;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitPlanVersionWriteRequest;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexBenefitPlanView;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexElectionAllocationRequest;
import com.acme.hrms.payroll.compensation.FlexBenefitPlanControls.FlexElectionValidationRequest;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class FlexBenefitPlanContractTest {
  private static final UUID COMPONENT = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID SUPPLEMENTAL = UUID.fromString("20000000-0000-0000-0000-000000000001");

  @Test
  void controllerReusesExistingCompensationStructurePermissions() {
    Map<String,String> permissions = Arrays.stream(FlexBenefitPlanController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
        .collect(Collectors.toMap(Method::getName, method -> method.getAnnotation(PreAuthorize.class).value()));
    assertThat(permissions)
        .containsEntry("create","hasAuthority('compensation.structure.create')")
        .containsEntry("list","hasAuthority('compensation.structure.read')")
        .containsEntry("addVersion","hasAuthority('compensation.structure.version.create')")
        .containsEntry("correct","hasAuthority('compensation.structure.version.correct')")
        .containsEntry("approve","hasAuthority('compensation.structure.approve')")
        .containsEntry("validateElection","hasAuthority('compensation.structure.simulate')")
        .containsEntry("audit","hasAuthority('audit.read')");
  }

  @Test
  void versionRequiresUnambiguousResidualAndElectionPolicy() {
    version("FORFEIT",null,null,null).validate();
    assertThatThrownBy(() -> version("CARRY_FORWARD",new BigDecimal("100.0000"),null,null).validate())
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("full annual basket");
    assertThatThrownBy(() -> version("TAXABLE_FALLBACK",null,null,null).validate())
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fallback component");
    assertThatThrownBy(() -> version("ENCASH",null,null,null).validate())
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("encashment component");
  }

  @Test
  void optionDefaultsCannotExceedLimits() {
    FlexBenefitOptionWriteRequest invalid = new FlexBenefitOptionWriteRequest(
        1,COMPONENT,new BigDecimal("500.0000"),new BigDecimal("1000.0000"),
        new BigDecimal("1200.0000"),true);
    assertThatThrownBy(invalid::validate).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("within option minimum and maximum");
  }

  @Test
  void electionValidationEnforcesLimitsWindowAndBasketWithoutEmployeePersistence() {
    FlexBenefitPlanView plan=view();
    var valid=FlexBenefitPlanControls.validateElection(plan,new FlexElectionValidationRequest(
        LocalDate.of(2027,1,15),null,false,false,false,false,Map.of(),
        List.of(new FlexElectionAllocationRequest(COMPONENT,new BigDecimal("700.0000")))));
    assertThat(valid.validationStatus()).isEqualTo("PASS");
    assertThat(valid.residualAnnualAmount()).isEqualByComparingTo("300.0000");
    assertThat(valid.residualTreatment()).isEqualTo("FORFEIT");

    var outside=FlexBenefitPlanControls.validateElection(plan,new FlexElectionValidationRequest(
        LocalDate.of(2027,3,1),null,false,false,false,false,Map.of(),
        List.of(new FlexElectionAllocationRequest(COMPONENT,new BigDecimal("700.0000")))));
    assertThat(outside.validationStatus()).isEqualTo("FAIL");
    assertThat(outside.blockers()).contains("ELECTION_OUTSIDE_CONFIGURED_WINDOW");

    var excess=FlexBenefitPlanControls.validateElection(plan,new FlexElectionValidationRequest(
        LocalDate.of(2027,1,15),null,false,false,false,false,Map.of(),
        List.of(new FlexElectionAllocationRequest(COMPONENT,new BigDecimal("1200.0000")))));
    assertThat(excess.validationStatus()).isEqualTo("FAIL");
    assertThat(excess.blockers()).contains("OPTION_ALLOCATION_OUTSIDE_CONFIGURED_LIMITS")
        .contains("ELECTION_EXCEEDS_APPROVED_ANNUAL_BASKET");
  }

  @Test
  void midYearChangesRequireConfiguredAuthority() {
    FlexBenefitPlanView plan=view();
    var blocked=FlexBenefitPlanControls.validateElection(plan,new FlexElectionValidationRequest(
        LocalDate.of(2027,3,1),null,true,false,false,false,Map.of(),List.of()));
    assertThat(blocked.blockers()).contains("MID_YEAR_CHANGE_REQUIRES_QUALIFYING_EVENT");
    var allowed=FlexBenefitPlanControls.validateElection(plan,new FlexElectionValidationRequest(
        LocalDate.of(2027,3,1),null,true,true,false,false,Map.of(),List.of()));
    assertThat(allowed.validationStatus()).isEqualTo("PASS");
  }

  private FlexBenefitPlanVersionWriteRequest version(
      String unusedRule,BigDecimal carryLimit,UUID fallback,UUID encashment) {
    return new FlexBenefitPlanVersionWriteRequest(
        "Synthetic Flex Plan","INR",SUPPLEMENTAL,null,new BigDecimal("1000.0000"),
        LocalDate.of(2027,1,1),LocalDate.of(2027,2,1),"DEFAULT_ELECTION",null,
        "QUALIFYING_EVENT_ONLY",unusedRule,carryLimit,fallback,encashment,"FORFEIT",
        "APPROVAL_REQUIRED",false,LocalDate.of(2027,1,1),LocalDate.of(2028,1,1),
        List.of(new FlexBenefitOptionWriteRequest(
            1,COMPONENT,BigDecimal.ZERO,new BigDecimal("1000.0000"),new BigDecimal("500.0000"),true)));
  }

  private FlexBenefitPlanView view() {
    FlexBenefitOptionView option=new FlexBenefitOptionView(
        UUID.randomUUID(),UUID.randomUUID(),COMPONENT,"MEAL","Meal benefit",1,BigDecimal.ZERO,
        new BigDecimal("1000.0000"),new BigDecimal("500.0000"),true,0);
    return new FlexBenefitPlanView(
        UUID.randomUUID(),"FLEX","ACTIVE",1,UUID.randomUUID(),1,0,"Flex","INR",
        UUID.randomUUID(),SUPPLEMENTAL,"BENEFITS","Benefits",1,null,null,null,
        new BigDecimal("1000.0000"),LocalDate.of(2027,1,1),LocalDate.of(2027,2,1),
        "DEFAULT_ELECTION",null,"QUALIFYING_EVENT_ONLY","FORFEIT",null,null,null,
        "FORFEIT","APPROVAL_REQUIRED",false,LocalDate.of(2027,1,1),LocalDate.of(2028,1,1),
        "APPROVED",java.time.Instant.parse("2026-12-01T00:00:00Z"),"checker",null,false,List.of(option));
  }
}
