package com.acme.hrms.payroll.platform;
import java.time.LocalDate;
public record EffectiveRange(LocalDate from, LocalDate toExclusive){public EffectiveRange{if(from==null)throw new IllegalArgumentException("from required");if(toExclusive!=null&&!toExclusive.isAfter(from))throw new IllegalArgumentException("half-open end must be after start");} public boolean contains(LocalDate d){return !d.isBefore(from)&&(toExclusive==null||d.isBefore(toExclusive));}}
