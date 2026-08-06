package com.acme.hrms.payroll.compensation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = false)
public record SalaryStructureSimulationRequest(
    @NotNull LocalDate effectiveDate,
    Map<String, JsonNode> eligibilityFacts) {

  public void validate() {
    if (effectiveDate == null) {
      throw new IllegalArgumentException(
          "effectiveDate is required");
    }
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String property, Object value) {
    throw new IllegalArgumentException(
        "Unknown simulation field: " + property);
  }
}
