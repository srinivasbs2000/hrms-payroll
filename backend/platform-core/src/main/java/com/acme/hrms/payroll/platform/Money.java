package com.acme.hrms.payroll.platform;
import java.math.*; import java.util.Currency;
public record Money(BigDecimal amount, Currency currency){ public Money{amount=amount.setScale(2,RoundingMode.HALF_UP);} public Money add(Money other){if(!currency.equals(other.currency))throw new IllegalArgumentException("Currency mismatch");return new Money(amount.add(other.amount),currency);} public static Money inr(String value){return new Money(new BigDecimal(value),Currency.getInstance("INR"));}}
