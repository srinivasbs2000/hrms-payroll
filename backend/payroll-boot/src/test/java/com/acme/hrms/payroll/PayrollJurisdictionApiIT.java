package com.acme.hrms.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class PayrollJurisdictionApiIT extends JrfApiITSupport {
  @Test
  void jurisdictionLifecycleIsSecuredMakerCheckedAndTenantScoped()
      throws Exception {
    String payload = """
        {
          "code":"IN_COUNTRY_API",
          "version":{
            "name":"India API jurisdiction",
            "countryCode":"IN",
            "levelCode":"COUNTRY",
            "levelRank":1,
            "effectiveFrom":"2026-01-01"
          }
        }
        """;

    mvc.perform(post("/api/v1/payroll-jurisdictions")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "g02h-jurisdiction-noauth")
            .content(payload))
        .andExpect(status().isUnauthorized());

    JsonNode draft = json(mvc.perform(post("/api/v1/payroll-jurisdictions")
            .with(token(TENANT_A, "jurisdiction-maker", "organisation.create"))
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "g02h-jurisdiction-create")
            .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("IN_COUNTRY_API"))
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andReturn());

    mvc.perform(post(
            "/api/v1/payroll-jurisdictions/{identityId}/versions/{versionId}/approval",
            draft.path("identityId").asText(),
            draft.path("versionId").asText())
            .with(token(TENANT_A, "jurisdiction-maker", "organisation.approve"))
            .header("Idempotency-Key", "test-key-aaaaaaaa")
            .header("If-Match", draft.path("versionNo").asText()))
        .andExpect(status().isForbidden());

    mvc.perform(post(
            "/api/v1/payroll-jurisdictions/{identityId}/versions/{versionId}/approval",
            draft.path("identityId").asText(),
            draft.path("versionId").asText())
            .with(token(TENANT_A, "jurisdiction-approver", "organisation.approve"))
            .header("Idempotency-Key", "test-key-bbbbbbbb")
            .header("If-Match", draft.path("versionNo").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
        .andExpect(jsonPath("$.identityStatus").value("ACTIVE"));

    mvc.perform(get("/api/v1/payroll-jurisdictions")
            .with(token(TENANT_A, "reader-a", "organisation.read"))
            .param("asOf", "2026-08-08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("IN_COUNTRY_API"));

    mvc.perform(get("/api/v1/payroll-jurisdictions")
            .with(token(TENANT_B, "reader-b", "organisation.read"))
            .param("asOf", "2026-08-08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    assertThat(tenantCount("organisation.payroll_jurisdiction", TENANT_A))
        .isEqualTo(1L);
    assertThat(tenantCount("organisation.payroll_jurisdiction", TENANT_B))
        .isZero();
  }

  @Test
  void futureDraftDoesNotHideCurrentApprovedJurisdiction()
      throws Exception {
    EntityRef current =
        createAndApproveJurisdiction("IN_COUNTRY_FUTURE_DRAFT");

    String successorPayload = """
        {
          "name":"India future jurisdiction",
          "countryCode":"IN",
          "levelCode":"COUNTRY",
          "levelRank":1,
          "effectiveFrom":"2027-01-01"
        }
        """;

    mvc.perform(
            post(
                    "/api/v1/payroll-jurisdictions/{identityId}/versions",
                    current.identityId())
                .with(
                    token(
                        TENANT_A,
                        "jurisdiction-version-maker",
                        "organisation.version.create"))
                .header(
                    "Idempotency-Key",
                    "jurisdiction-future-draft-version-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(successorPayload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.versionSequence").value(2))
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"));

    mvc.perform(
            get("/api/v1/payroll-jurisdictions")
                .with(
                    token(
                        TENANT_A,
                        "jurisdiction-current-reader",
                        "organisation.read"))
                .param("asOf", "2026-08-08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(
            jsonPath("$[0].versionId").value(current.versionId()));
  }

}
