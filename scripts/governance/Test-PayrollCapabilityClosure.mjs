#!/usr/bin/env node
import { selfTest } from "./payroll-capability-closure.mjs";

try {
  await selfTest();
} catch (error) {
  console.error(error?.stack ?? error);
  process.exitCode = 1;
}
