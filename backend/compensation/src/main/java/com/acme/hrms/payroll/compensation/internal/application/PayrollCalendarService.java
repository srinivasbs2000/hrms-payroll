package com.acme.hrms.payroll.compensation.internal.application;

import com.acme.hrms.payroll.compensation.GeneratePeriodsRequest;
import com.acme.hrms.payroll.compensation.PayPeriodOperationalView;
import com.acme.hrms.payroll.compensation.PayPeriodView;
import com.acme.hrms.payroll.compensation.PayrollCalendarHolidayView;
import com.acme.hrms.payroll.compensation.PayrollCalendarHolidayWriteRequest;
import com.acme.hrms.payroll.compensation.PayrollCalendarLifecycleRequest;
import com.acme.hrms.payroll.compensation.PayrollCalendarMilestoneRuleView;
import com.acme.hrms.payroll.compensation.PayrollCalendarMilestoneRulesRequest;
import com.acme.hrms.payroll.compensation.PayrollCalendarOperationalView;
import com.acme.hrms.payroll.compensation.PayrollCalendarReadinessView;
import com.acme.hrms.payroll.compensation.PayrollCalendarView;
import com.acme.hrms.payroll.compensation.PayrollCalendarWriteRequest;
import com.acme.hrms.payroll.compensation.internal.infrastructure.PayrollCalendarRepository;
import com.acme.hrms.payroll.integrations.CanonicalJsonHasher;
import com.acme.hrms.payroll.integrations.IdempotencyStore;
import com.acme.hrms.payroll.integrations.OutboxWriter;
import com.acme.hrms.payroll.platform.AuditReader;
import com.acme.hrms.payroll.platform.AuditWriter;
import com.acme.hrms.payroll.platform.AuthenticatedActor;
import com.acme.hrms.payroll.platform.ConflictException;
import com.acme.hrms.payroll.platform.DomainEventFactory;
import com.acme.hrms.payroll.platform.TenantContext;
import com.acme.hrms.payroll.platform.TenantTransactionExecutor;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PayrollCalendarService {
  private static final String OBJECT_TYPE = "PAYROLL_CALENDAR";

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

  public PayrollCalendarView create(String key, PayrollCalendarWriteRequest request) {
    request.validate();
    return idempotentCalendar("calendar:create", key, request, () -> {
      PayrollCalendarView created = repository.create(request, actor.require(), clock.instant());
      recordCreated(created);
      return created;
    });
  }

  public List<PayrollCalendarView> list() {
    return transactions.read(repository::list);
  }

  public List<PayPeriodView> generate(
      UUID calendarId, String key, GeneratePeriodsRequest request) {
    PayrollCalendarView calendar = transactions.read(() -> repository.calendar(calendarId));
    request.validateFor(calendar.frequency());
    Map<String, Object> command = new LinkedHashMap<>();
    command.put("calendarId", calendarId);
    command.put("year", request.year());
    command.put("paymentDay", request.paymentDay());
    command.put("startDate", request.startDate());
    command.put("periodCount", request.periodCount());

    return idempotentPeriods("calendar:period-generate:" + calendarId, key, command, () -> {
      List<PayPeriodView> generated = repository.generate(
          calendarId, request, actor.require(), clock.instant());
      recordGenerated(calendar, request, generated);
      return generated;
    });
  }

  public List<PayPeriodView> periods(UUID calendarId, Integer year) {
    validateYear(year);
    return transactions.read(() -> repository.periods(calendarId, year));
  }

  public List<PayrollCalendarMilestoneRuleView> milestoneRules(UUID calendarId) {
    return transactions.read(() -> repository.milestoneRules(calendarId));
  }

  public List<PayrollCalendarMilestoneRuleView> configureMilestoneRules(
      UUID calendarId, String key, PayrollCalendarMilestoneRulesRequest request) {
    request.validate();
    return idempotentMilestoneRules(
        "calendar:milestone-rules:" + calendarId, key, request, () -> {
          PayrollCalendarView calendar = repository.calendar(calendarId);
          String principal = actor.require();
          List<PayrollCalendarMilestoneRuleView> before =
              repository.milestoneRules(calendarId);
          List<PayrollCalendarMilestoneRuleView> configured = configureCalendarChild(
              () -> repository.configureMilestoneRules(
                  calendarId, request.rules(), principal, clock.instant()));
          recordMilestoneRules(calendar, before, configured, principal);
          return configured;
        });
  }

  public List<PayrollCalendarHolidayView> holidays(UUID calendarId) {
    return transactions.read(() -> repository.holidays(calendarId));
  }

  public PayrollCalendarHolidayView configureHoliday(
      UUID calendarId, String key, PayrollCalendarHolidayWriteRequest request) {
    request.validate();
    return idempotentHoliday("calendar:holiday:" + calendarId, key, request, () -> {
      PayrollCalendarView calendar = repository.calendar(calendarId);
      String principal = actor.require();
      PayrollCalendarHolidayView before =
          repository.holiday(calendarId, request.holidayDate());
      PayrollCalendarHolidayView configured = configureCalendarChild(
          () -> repository.configureHoliday(calendarId, request, principal, clock.instant()));
      recordHoliday(calendar, before, configured, principal);
      return configured;
    });
  }

  public PayrollCalendarReadinessView readiness(UUID calendarId) {
    return transactions.read(() -> repository.readiness(calendarId));
  }

  public PayrollCalendarOperationalView publish(
      UUID calendarId, String key, PayrollCalendarLifecycleRequest request) {
    return idempotentOperational("calendar:publish:" + calendarId, key, request, () -> {
      String principal = actor.require();
      repository.publish(calendarId, request.reason(), principal, clock.instant());
      PayrollCalendarOperationalView view = repository.operations(calendarId);
      recordLifecycle("PUBLISHED", calendarId, view, principal);
      return view;
    });
  }

  public PayrollCalendarView amend(UUID calendarId, String key) {
    return idempotentCalendar("calendar:amend:" + calendarId, key, Map.of("calendarId", calendarId), () -> {
      String principal = actor.require();
      PayrollCalendarView created = repository.amend(calendarId, principal, clock.instant());
      recordLifecycle("AMENDMENT_DRAFT_CREATED", calendarId, created.id(), principal);
      return created;
    });
  }

  public PayrollCalendarOperationalView retire(
      UUID calendarId, String key, PayrollCalendarLifecycleRequest request) {
    request.requireReason();
    return idempotentOperational("calendar:retire:" + calendarId, key, request, () -> {
      String principal = actor.require();
      repository.retire(calendarId, request.reason(), principal, clock.instant());
      PayrollCalendarOperationalView view = repository.operations(calendarId);
      recordLifecycle("RETIRED", calendarId, view, principal);
      return view;
    });
  }

  public PayrollCalendarOperationalView operations(UUID calendarId) {
    return transactions.read(() -> repository.operations(calendarId));
  }

  public List<PayPeriodOperationalView> periodOperations(UUID calendarId, Integer year) {
    validateYear(year);
    return transactions.read(() -> repository.periodOperations(calendarId, year));
  }

  public List<AuditReader.AuditEventView> audit(UUID calendarId) {
    return transactions.read(() -> auditReader.forObject(OBJECT_TYPE, calendarId));
  }

  private void validateYear(Integer year) {
    if (year != null && (year < 2020 || year > 2100)) {
      throw new IllegalArgumentException("year must be between 2020 and 2100");
    }
  }

  private void recordCreated(PayrollCalendarView created) {
    String principal = actor.require();
    Map<String, Object> after = calendarState(created);
    audit.append("CREATED", OBJECT_TYPE, created.id(), null, after, Map.of(), principal);
    outbox.append(events.create(
        "PayrollCalendarCreated", 1, TenantContext.require(), null, OBJECT_TYPE,
        created.id(), created.calendarVersion(), after));
  }

  private void recordGenerated(
      PayrollCalendarView calendar, GeneratePeriodsRequest request, List<PayPeriodView> periods) {
    String principal = actor.require();
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("calendar", calendarState(calendar));
    after.put("periodCount", periods.size());
    after.put("firstPeriodCode", periods.getFirst().periodCode());
    after.put("lastPeriodCode", periods.getLast().periodCode());
    after.put("year", request.year());
    after.put("startDate", request.startDate());
    audit.append("PERIODS_GENERATED", OBJECT_TYPE, calendar.id(), null, after, Map.of(), principal);
    outbox.append(events.create(
        "PayrollCalendarPeriodsGenerated", 1, TenantContext.require(), null, OBJECT_TYPE,
        calendar.id(), calendar.calendarVersion(), after));
  }

  private void recordLifecycle(
      String action, UUID sourceCalendarId, Object afterValue, String principal) {
    Map<String, Object> after = Map.of(
        "sourceCalendarId", sourceCalendarId,
        "result", afterValue);
    audit.append(action, OBJECT_TYPE, sourceCalendarId, null, after, Map.of(), principal);
    outbox.append(events.create(
        "PayrollCalendar" + action, 1, TenantContext.require(), null, OBJECT_TYPE,
        sourceCalendarId, 1, after));
  }

  private void recordMilestoneRules(
      PayrollCalendarView calendar,
      List<PayrollCalendarMilestoneRuleView> before,
      List<PayrollCalendarMilestoneRuleView> configured,
      String principal) {
    Map<String, Object> beforeState = Map.of(
        "calendarId", calendar.id(),
        "configuration", before);
    Map<String, Object> afterState = Map.of(
        "calendarId", calendar.id(),
        "configuration", configured);
    audit.append(
        "MILESTONE_RULES_CONFIGURED", OBJECT_TYPE, calendar.id(),
        beforeState, afterState, Map.of(), principal);
    for (PayrollCalendarMilestoneRuleView rule : configured) {
      Map<String, Object> payload = Map.of(
          "calendarId", calendar.id(),
          "rule", rule);
      outbox.append(events.create(
          "PayrollCalendarMilestoneRuleConfigured", 1, TenantContext.require(), null,
          "PAYROLL_CALENDAR_MILESTONE_RULE", rule.id(), rule.versionNo(), payload));
    }
  }

  private void recordHoliday(
      PayrollCalendarView calendar,
      PayrollCalendarHolidayView before,
      PayrollCalendarHolidayView configured,
      String principal) {
    Map<String, Object> beforeState = before == null ? null : Map.of(
        "calendarId", calendar.id(),
        "configuration", before);
    Map<String, Object> afterState = Map.of(
        "calendarId", calendar.id(),
        "configuration", configured);
    audit.append(
        "HOLIDAY_CONFIGURED", OBJECT_TYPE, calendar.id(),
        beforeState, afterState, Map.of(), principal);
    outbox.append(events.create(
        "PayrollCalendarHolidayConfigured", 1, TenantContext.require(), null,
        "PAYROLL_CALENDAR_HOLIDAY", configured.id(), configured.versionNo(), afterState));
  }

  private Map<String, Object> calendarState(PayrollCalendarView view) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", view.id());
    state.put("calendarSeriesId", view.calendarSeriesId());
    state.put("calendarVersion", view.calendarVersion());
    state.put("supersedesCalendarId", view.supersedesCalendarId());
    state.put("code", view.code());
    state.put("name", view.name());
    state.put("frequency", view.frequency());
    state.put("timezone", view.timezone());
    return state;
  }

  private PayrollCalendarView idempotentCalendar(
      String operation, String key, Object request, Supplier<PayrollCalendarView> work) {
    requireKey(key);
    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        verifyReplay(saved.get(), requestHash);
        return readSaved(saved.get(), PayrollCalendarView.class, "Stored calendar response is invalid");
      }
      reserve(operation, key, requestHash);
      PayrollCalendarView response = work.get();
      idempotency.complete(operation, key, 201, response);
      return response;
    });
  }

  private PayrollCalendarOperationalView idempotentOperational(
      String operation, String key, Object request, Supplier<PayrollCalendarOperationalView> work) {
    requireKey(key);
    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        verifyReplay(saved.get(), requestHash);
        return readSaved(saved.get(), PayrollCalendarOperationalView.class,
            "Stored calendar operational response is invalid");
      }
      reserve(operation, key, requestHash);
      PayrollCalendarOperationalView response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }

  private List<PayPeriodView> idempotentPeriods(
      String operation, String key, Object request, Supplier<List<PayPeriodView>> work) {
    requireKey(key);
    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        verifyReplay(saved.get(), requestHash);
        try {
          return objectMapper.readValue(
              saved.get().body(), new TypeReference<List<PayPeriodView>>() {});
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException("Stored period response is invalid", exception);
        }
      }
      reserve(operation, key, requestHash);
      List<PayPeriodView> response = work.get();
      idempotency.complete(operation, key, 201, response);
      return response;
    });
  }

  private List<PayrollCalendarMilestoneRuleView> idempotentMilestoneRules(
      String operation,
      String key,
      Object request,
      Supplier<List<PayrollCalendarMilestoneRuleView>> work) {
    requireKey(key);
    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        verifyReplay(saved.get(), requestHash);
        try {
          return objectMapper.readValue(
              saved.get().body(),
              new TypeReference<List<PayrollCalendarMilestoneRuleView>>() {});
        } catch (JsonProcessingException exception) {
          throw new IllegalStateException(
              "Stored milestone-rule response is invalid", exception);
        }
      }
      reserve(operation, key, requestHash);
      List<PayrollCalendarMilestoneRuleView> response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }

  private PayrollCalendarHolidayView idempotentHoliday(
      String operation,
      String key,
      Object request,
      Supplier<PayrollCalendarHolidayView> work) {
    requireKey(key);
    return transactions.write(() -> {
      String requestHash = canonical.hash(request);
      var saved = idempotency.find(operation, key);
      if (saved.isPresent()) {
        verifyReplay(saved.get(), requestHash);
        return readSaved(
            saved.get(), PayrollCalendarHolidayView.class,
            "Stored holiday response is invalid");
      }
      reserve(operation, key, requestHash);
      PayrollCalendarHolidayView response = work.get();
      idempotency.complete(operation, key, 200, response);
      return response;
    });
  }

  private <T> T configureCalendarChild(Supplier<T> work) {
    try {
      return work.get();
    } catch (DataIntegrityViolationException exception) {
      throw new IllegalArgumentException(
          "Payroll calendar configuration is immutable or invalid", exception);
    }
  }

  private <T> T readSaved(IdempotencyStore.SavedResponse saved, Class<T> type, String message) {
    try {
      return objectMapper.readValue(saved.body(), type);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(message, exception);
    }
  }

  private void reserve(String operation, String key, String requestHash) {
    try {
      idempotency.reserve(operation, key, requestHash, clock.instant().plus(Duration.ofHours(24)));
    } catch (IllegalStateException exception) {
      throw new ConflictException("Idempotency-Key is already in use", exception);
    }
  }

  private void verifyReplay(IdempotencyStore.SavedResponse saved, String requestHash) {
    if (!saved.requestHash().equals(requestHash)) {
      throw new ConflictException("Idempotency-Key was already used with a different request");
    }
    if (!saved.completed()) {
      throw new ConflictException("Idempotent operation is still in progress");
    }
  }

  private void requireKey(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key is required");
    }
  }
}
