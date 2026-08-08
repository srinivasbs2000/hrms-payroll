package com.acme.hrms.payroll;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class RegistrationReadinessApiIT extends JrfApiITSupport {
  @Test
  void readinessIsBoundedToExactOwnerTypeJurisdictionAndTenant()
      throws Exception {
    EntityRef jurisdiction = createAndApproveJurisdiction("IN_COUNTRY_READY");
    EntityRef type = createAndApproveRegistrationType("GENERIC_REG_READY");
    ActiveRegistration active = createActiveRegistration(
        "REG_READY",
        jurisdiction,
        type);

    String readyPayload = readinessPayload(
        type.identityId(),
        LEGAL_ID,
        jurisdiction.identityId());

    mvc.perform(post("/api/v1/foundation-readiness/jurisdiction-registration")
            .with(token(
                TENANT_A,
                "readiness-reader",
                "statutory-registration.read"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(readyPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(true))
        .andExpect(jsonPath("$.registrationVersionId").value(active.versionId()))
        .andExpect(jsonPath("$.findings.length()").value(0));

    String missingOwnerPayload = readinessPayload(
        type.identityId(),
        "51000000-0000-0000-0000-000000000099",
        jurisdiction.identityId());

    mvc.perform(post("/api/v1/foundation-readiness/jurisdiction-registration")
            .with(token(
                TENANT_A,
                "readiness-reader",
                "statutory-registration.read"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(missingOwnerPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(false))
        .andExpect(jsonPath("$.findings[0].code").value("REGISTRATION_MISSING"))
        .andExpect(jsonPath("$.findings[0].severity").value("BLOCKER"));

    mvc.perform(post("/api/v1/foundation-readiness/jurisdiction-registration")
            .with(token(TENANT_A, "wrong-permission", "organisation.read"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(readyPayload))
        .andExpect(status().isForbidden());

    mvc.perform(post("/api/v1/foundation-readiness/jurisdiction-registration")
            .with(token(
                TENANT_B,
                "readiness-reader-b",
                "statutory-registration.read"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(readyPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(false))
        .andExpect(jsonPath("$.findings[0].code").value("REGISTRATION_MISSING"));
  }

  @Test
  void parentSuspensionBlocksChildReadiness()
      throws Exception {
    EntityRef jurisdiction =
        createAndApproveJurisdiction("IN_COUNTRY_PARENT_READY");
    EntityRef parentType =
        createAndApproveRegistrationType("PARENT_READY");
    EntityRef childType =
        createAndApproveRegistrationType(
            "CHILD_READY",
            parentType);

    ActiveRegistration parent =
        createActiveRegistration(
            "REG_PARENT_READY",
            jurisdiction,
            parentType);
    ActiveRegistration child =
        createActiveRegistration(
            "REG_CHILD_READY",
            jurisdiction,
            childType,
            parent);

    String childPayload =
        readinessPayload(
            childType.identityId(),
            LEGAL_ID,
            jurisdiction.identityId());

    mvc.perform(
            post(
                    "/api/v1/foundation-readiness/jurisdiction-registration")
                .with(
                    token(
                        TENANT_A,
                        "child-readiness-reader",
                        "statutory-registration.read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(childPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(true))
        .andExpect(
            jsonPath("$.registrationVersionId")
                .value(child.versionId()));

    mvc.perform(
            post(
                    "/api/v1/statutory-registrations/{identityId}/versions/{versionId}/suspension",
                    parent.identityId(),
                    parent.versionId())
                .with(
                    token(
                        TENANT_A,
                        "independent-parent-suspender",
                        "statutory-registration.approve"))
                .header(
                    "Idempotency-Key",
                    "parent-ready-suspension-0001")
                .header(
                    "If-Match",
                    Long.toString(parent.versionNo()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"reason\":\"parent suspended for readiness verification\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("SUSPENDED"));

    mvc.perform(
            post(
                    "/api/v1/foundation-readiness/jurisdiction-registration")
                .with(
                    token(
                        TENANT_A,
                        "child-readiness-reader",
                        "statutory-registration.read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(childPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(false))
        .andExpect(
            jsonPath("$.findings[0].code")
                .value("PARENT_REGISTRATION_INVALID"))
        .andExpect(
            jsonPath("$.findings[0].severity")
                .value("BLOCKER"));
  }

  @Test
  void renewalDraftKeepsCurrentRegistrationVisibleAndReady()
      throws Exception {
    EntityRef jurisdiction =
        createAndApproveJurisdiction("IN_COUNTRY_RENEWAL_READY");
    EntityRef renewalType =
        createAndApproveRegistrationType("RENEWAL_READY");
    ActiveRegistration current =
        createActiveRegistration(
            "REG_RENEWAL_READY",
            jurisdiction,
            renewalType);

    String renewalPayload = """
        {
          "registrationTypeId":"%s",
          "registrationTypeVersionId":"%s",
          "identifier":"renew-456",
          "ownerKind":"LEGAL_ENTITY",
          "ownerId":"%s",
          "payrollJurisdictionId":"%s",
          "payrollJurisdictionVersionId":"%s",
          "effectiveFrom":"2027-01-01"
        }
        """.formatted(
            renewalType.identityId(),
            renewalType.versionId(),
            LEGAL_ID,
            jurisdiction.identityId(),
            jurisdiction.versionId());

    mvc.perform(
            post(
                    "/api/v1/statutory-registrations/{identityId}/versions",
                    current.identityId())
                .with(
                    token(
                        TENANT_A,
                        "renewal-maker",
                        "statutory-registration.write"))
                .header(
                    "Idempotency-Key",
                    "renewal-draft-create-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(renewalPayload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lifecycleStatus").value("DRAFT"));

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/statutory-registrations")
                .with(
                    token(
                        TENANT_A,
                        "registration-reader-renewal",
                        "statutory-registration.read"))
                .param("asOf", "2026-08-08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(
            jsonPath("$[0].referenceCode")
                .value("REG_RENEWAL_READY"))
        .andExpect(
            jsonPath("$[0].versionId")
                .value(current.versionId()));

    String renewalReadinessPayload =
        readinessPayload(
            renewalType.identityId(),
            LEGAL_ID,
            jurisdiction.identityId());

    mvc.perform(
            post(
                    "/api/v1/foundation-readiness/jurisdiction-registration")
                .with(
                    token(
                        TENANT_A,
                        "renewal-readiness-reader",
                        "statutory-registration.read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(renewalReadinessPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(true))
        .andExpect(
            jsonPath("$.registrationVersionId")
                .value(current.versionId()))
        .andExpect(
            jsonPath("$.findings[0].code")
                .value("REGISTRATION_RENEWAL_DRAFT"))
        .andExpect(
            jsonPath("$.findings[0].severity")
                .value("WARNING"));
  }

  private String readinessPayload(
      String typeId,
      String ownerId,
      String jurisdictionId) {
    return """
        {
          "registrationTypeId":"%s",
          "ownerKind":"LEGAL_ENTITY",
          "ownerId":"%s",
          "payrollJurisdictionId":"%s",
          "asOf":"2026-08-08",
          "warningHorizonDays":45
        }
        """.formatted(typeId, ownerId, jurisdictionId);
  }
}
