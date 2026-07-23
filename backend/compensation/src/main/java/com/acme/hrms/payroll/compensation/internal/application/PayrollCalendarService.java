package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation
    .GeneratePeriodsRequest;
import com.acme.hrms.payroll.compensation.PayPeriodView;
import com.acme.hrms.payroll.compensation
    .PayrollCalendarView;
import com.acme.hrms.payroll.compensation
    .PayrollCalendarWriteRequest;
import com.acme.hrms.payroll.compensation.internal
    .infrastructure.PayrollCalendarRepository;
import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.platform.AuditReader;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.DomainEventFactory;
import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.platform
    .TenantTransactionExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class PayrollCalendarService {
  private static final String OBJECT_TYPE =
      "PAYROLL_CALENDAR";

  private final PayrollCalendarRepository repository;
  private final TenantTransactionExecutor transactions;
  private final AuthenticatedActor actor;
  private final Clock clock;
  private final AuditWriter audit;
  private final AuditReader auditReader;
  private final DomainEventFactory events;
  private final OutboxWriter outbox;
  private final IdempotencyStore idempotency;
  private final CanonicalJsonHasher canonical;
  private final ObjectMapper objectMapper;

  public PayrollCalendarService(
      PayrollCalendarRepository repository,
      TenantTransactionExecutor transactions,
      AuthenticatedActor actor,
      Clock clock,
      AuditWriter audit,
      AuditReader auditReader,
      DomainEventFactory events,
      OutboxWriter outbox,
      IdempotencyStore idempotency,
      CanonicalJsonHasher canonical,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.transactions = transactions;
    this.actor = actor;
    this.clock = clock;
    this.audit = audit;
    this.auditReader = auditReader;
    this.events = events;
    this.outbox = outbox;
    this.idempotency = idempotency;
    this.canonical = canonical;
    this.objectMapper = objectMapper;
  }

  public PayrollCalendarView create(
      String key, PayrollCalendarWriteRequest request) {
    request.validate();
    return idempotentCalendar(
        "calendar:create",
        key,
        request,
        () -> {
          PayrollCalendarView created =
              repository.create(
                  request,
                  actor.require(),
                  clock.instant());
          recordCreated(created);
          return created;
        });
  }

  public List<PayrollCalendarView> list() {
    return transactions.read(repository::list);
  }

  public List<PayPeriodView> generate(
      UUID calendarId,
      String key,
      GeneratePeriodsRequest request) {
    request.validate();
    Map<String, Object> command = new LinkedHashMap<>();
    command.put("calendarId", calendarId);
    command.put("year", request.year());
    command.put(
        "paymentDay",
        request.resolvedPaymentDay());

    return idempotentPeriods(
        "calendar:period-generate:" + calendarId,
        key,
        command,
        () -> {
          PayrollCalendarView calendar =
              repository.calendar(calendarId);
          List<PayPeriodView> generated =
              repository.generate(
                  calendarId,
                  request.year(),
                  request.resolvedPaymentDay(),
                  actor.require(),
                  clock.instant());
          recordGenerated(
              calendar,
              request,
              generated);
          return generated;
        });
  }

  public List<PayPeriodView> periods(
      UUID calendarId, Integer year) {
    if (year != null && (year < 2020 || year > 2100)) {
      throw new IllegalArgumentException(
          "year must be between 2020 and 2100");
    }
    return transactions.read(
        () -> repository.periods(calendarId, year));
  }

  public List<AuditReader.AuditEventView> audit(
      UUID calendarId) {
    return transactions.read(
        () -> auditReader.forObject(
            OBJECT_TYPE,
            calendarId));
  }

  private void recordCreated(
      PayrollCalendarView created) {
    String principal = actor.require();
    Map<String, Object> after = calendarState(created);
    audit.append(
        "CREATED",
        OBJECT_TYPE,
        created.id(),
        null,
        after,
        Map.of(),
        principal);
    outbox.append(events.create(
        "PayrollCalendarCreated",
        1,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        created.id(),
        1,
        after));
  }

  private void recordGenerated(
      PayrollCalendarView calendar,
      GeneratePeriodsRequest request,
      List<PayPeriodView> periods) {
    String principal = actor.require();
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("calendar", calendarState(calendar));
    after.put("year", request.year());
    after.put(
        "paymentDay",
        request.resolvedPaymentDay());
    after.put("periodCount", periods.size());
    after.put(
        "firstPeriodCode",
        periods.getFirst().periodCode());
    after.put(
        "lastPeriodCode",
        periods.getLast().periodCode());

    audit.append(
        "PERIODS_GENERATED",
        OBJECT_TYPE,
        calendar.id(),
        null,
        after,
        Map.of("year", request.year()),
        principal);
    outbox.append(events.create(
        "PayrollCalendarPeriodsGenerated",
        1,
        TenantContext.require(),
        null,
        OBJECT_TYPE,
        calendar.id(),
        request.year(),
        after));
  }

  private Map<String, Object> calendarState(
      PayrollCalendarView view) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", view.id());
    state.put("code", view.code());
    state.put("name", view.name());
    state.put("frequency", view.frequency());
    state.put("timezone", view.timezone());
    return state;
  }

  private PayrollCalendarView idempotentCalendar(
      String operation,
      String key,
      Object request,
      Supplier<PayrollCalendarView> work) {
    requireKey(key);
    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        verifyReplay(saved.get(), requestHash);
        try {
          return objectMapper.readValue(
              saved.get().body(),
              PayrollCalendarView.class);
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException(
              "Stored calendar response is invalid",
              exception);
        }
      }

      reserve(operation, key, requestHash);
      PayrollCalendarView response = work.get();
      idempotency.complete(
          operation, key, 201, response);
      return response;
    });
  }

  private List<PayPeriodView> idempotentPeriods(
      String operation,
      String key,
      Object request,
      Supplier<List<PayPeriodView>> work) {
    requireKey(key);
    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        verifyReplay(saved.get(), requestHash);
        try {
          return objectMapper.readValue(
              saved.get().body(),
              new TypeReference<List<PayPeriodView>>() {});
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException(
              "Stored period response is invalid",
              exception);
        }
      }

      reserve(operation, key, requestHash);
      List<PayPeriodView> response = work.get();
      idempotency.complete(
          operation, key, 201, response);
      return response;
    });
  }

  private void reserve(
      String operation,
      String key,
      String requestHash) {
    try {
      idempotency.reserve(
          operation,
          key,
          requestHash,
          clock.instant().plus(
              Duration.ofHours(24)));
    } catch (IllegalStateException exception) {
      throw new ConflictException(
          "Idempotency-Key is already in use",
          exception);
    }
  }

  private void verifyReplay(
      IdempotencyStore.SavedResponse saved,
      String requestHash) {
    if (!saved.requestHash().equals(requestHash)) {
      throw new ConflictException(
          "Idempotency-Key was already used "
              + "with a different request");
    }
    if (!saved.completed()) {
      throw new ConflictException(
          "Idempotent operation is still in progress");
    }
  }

  private void requireKey(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException(
          "Idempotency-Key is required");
    }
  }
}
