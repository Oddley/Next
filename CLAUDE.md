# Next — Project Context

Native Android TODO app: foster mama's working stack with controlled deferral.

The defining mechanic is **snooze + offset**: she can push the top item(s) down temporarily without losing them, creating a new "current top" that stays prominent until she actively engages with it. The persistent notification keeps that current top in her face — by design, it's the only TODO that matters until she clears it or pushes it.

## Standards (read before writing any code)

| Concern | ADR |
|---|---|
| Tech stack | [ADR-001](docs/adr/001-tech-stack.md) |
| Architecture (Domain/Data/UI/Notification) | [ADR-002](docs/adr/002-architecture.md) |
| Testing (Red-Green TDD on domain) | [ADR-003](docs/adr/003-tdd.md) |
| Null handling (Nothing is Something) | [ADR-004](docs/adr/004-nothing-is-something.md) |
| Documentation | [ADR-005](docs/adr/005-documentation.md) |
| Git workflow | [ADR-006](docs/adr/006-git-workflow.md) |
| Notification (foreground service) | [ADR-007](docs/adr/007-notification-strategy.md) |
| Snooze data model | [ADR-008](docs/adr/008-snooze-data-model.md) |
| Drive sync (manual push/pull) | [ADR-009](docs/adr/009-drive-sync-strategy.md) |

## Directory Map (target structure once code lands)

```
app/
  src/
    main/
      kotlin/com/oddley/next/
        domain/        ← pure Kotlin, JVM-tested (snooze math, list ops)
          [concept]/
            CLAUDE.md  ← domain contract: inputs, outputs, invariants
        data/          ← Room + Drive REST wrappers
          CLAUDE.md
        ui/            ← Compose screens + ViewModels
          CLAUDE.md
        notification/  ← foreground service + notification builders
          CLAUDE.md
        app/           ← Application class, DI wiring
          CLAUDE.md
      res/             ← Android resources
      AndroidManifest.xml
    test/              ← JVM unit tests (domain only per ADR-003)
docs/
  spec.md              ← feature spec
  plan.md              ← phased implementation
  adr/                 ← one file per architectural decision
```

## Key Invariants (override everything else)

1. **Domain code is pure Kotlin and fully tested.** No Android imports, no Room, no Compose. Compiles in a plain JVM module.
2. **Snooze is zero-or-one**, never more. Computing top of stack honors the offset whether the session is expired or not. Only "Mark complete" or "Snooze" on an *expired* session clears it.
3. **Cross-off preserves order.** Items don't reorder when crossed off — they're filtered between two views (active / crossed) while keeping their relative positions.
4. **Absence is typed.** No nullable returns in domain code — use sealed types or Null Objects (ADR-004).
5. **Red before green.** No domain feature code without a failing test first.
6. **The notification is the app's main interface.** The full list view is for editing; the notification is the daily-driver surface. If a feature lives only in the list view, foster mama may never see it.

## What this app is NOT

- **Not a sync tool.** Drive is a manual export/import for backup + device transfer. No conflict resolution, no live sync, no multi-user. One person, one device at a time.
- **Not a calendar.** No due dates, no reminders, no time-of-day. Just an ordered list with snooze.
- **Not a project management tool.** No categories, tags, subtasks, dependencies. Single flat list.
