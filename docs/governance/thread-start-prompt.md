# Standard Project-Thread Start Prompt

Paste this once into each HRMS Payroll project thread:

```text
Continue the HRMS Payroll project using the repository as the single source of
truth. Before answering or changing anything, read AGENTS.md,
docs/design/hrms-payroll-master-design.md,
docs/design/decision-register.md,
docs/runbooks/project-continuation-handoff.md and
docs/governance/thread-registry.md.

Then validate them against the current local working tree and live GitHub
branch/PR/CI. Mark unknown facts NOT VERIFIED and disagreements DOCUMENTATION
CONFLICT. Do not reconstruct state from chat memory.

Register this thread's role, scope, branch/PR, exact file allow-list and
migration reservation before writes. Only one thread may own overlapping files
or the next migration at a time.

Maintain the living documents according to
docs/governance/thread-maintenance-protocol.md. Update the running handoff and
thread registry before every thread transition. Update the master design and
decision register only when their documented triggers are met.

Do not stage, commit, push, update PR metadata or merge unless I explicitly
authorise that action.
```

For an existing thread, append:

```text
This is Thread <number/name>. Recover its actual current scope from repository
evidence and previous handoff artifacts; do not guess.
```
