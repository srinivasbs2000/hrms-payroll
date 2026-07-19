package com.acme.hrms.payroll.platform;
import java.util.UUID;
public final class TenantContext { private static final ThreadLocal<UUID> CURRENT=new ThreadLocal<>(); private TenantContext(){} public static UUID require(){var v=CURRENT.get();if(v==null)throw new IllegalStateException("Tenant context missing");return v;} public static void set(UUID id){CURRENT.set(id);} public static void clear(){CURRENT.remove();} }
