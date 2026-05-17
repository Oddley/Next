# ADR-005: Documentation Conventions

**Status:** Accepted

## Context

We want code that an AI collaborator (or a future-self) can pick up cold and contribute to without reverse-engineering intent from implementation. Doc proximity to code is the lever — docs that live in the same folder as the code they describe get read; docs in a separate wiki don't.

## Decision

### Per-folder `CLAUDE.md`

Every significant directory has a `CLAUDE.md` that describes:

- **Purpose** — what this folder is for in one sentence
- **Contract** — what callers can expect; invariants this folder upholds
- **Files** — table of files in this folder and their roles (if non-obvious)
- **Dependencies** — what this folder imports from and depends on
- **Anti-patterns** — what NOT to put here, with examples

Required folders:
- Top-level `app/src/main/kotlin/com/oddley/next/` itself
- Each subfolder under that (`domain/`, `data/`, `ui/`, `notification/`, `app/`)
- Each sub-concept under `domain/` (`domain/task/`, `domain/snooze/`, `domain/sync/`)

### ADRs (`docs/adr/`)

One file per architectural decision. Numbered sequentially, never renumbered. Format:

```
# ADR-NNN: <Short title>

**Status:** Accepted | Superseded by ADR-XXX | Deprecated

## Context
What's the situation that prompted this decision?

## Decision
What did we decide? Be specific.

## Rationale
Why this over alternatives?

## Consequences
What does this commit us to? What does it enable / prevent?

## Alternatives considered (optional)
What did we reject and why?
```

### Spec + plan (`docs/spec.md` + `docs/plan.md`)

Living documents that describe what the app does (spec) and how we're building it incrementally (plan). Updated as scope evolves; commit history captures the why.

### Code comments

- **Comments explain WHY, not WHAT.** Names explain what.
- If a comment is needed to explain what a function does, rename the function.
- Multi-line `//` blocks above non-obvious mechanisms (race-prone, deliberate-quirk, Android-API-gotcha) are encouraged.

## Rationale

- Per-folder `CLAUDE.md` files are read on demand when an AI agent (or human) navigates into the folder — high signal, low search cost
- ADRs preserve the WHY behind decisions; without them, future-us re-litigates settled choices
- A spec + plan pairing keeps "what" and "how" separable; the spec rarely changes once a feature ships, the plan is more iterative

## Consequences

- More documentation to maintain. Mitigation: keep each piece short and focused.
- Reading a new folder takes 30 seconds (CLAUDE.md) instead of 10 minutes (reverse-engineering)
- Architectural decisions are visible and challengeable — a new contributor can read ADR-002 and propose a better architecture rather than discovering and arguing against the implicit one

## Anti-patterns

- A `CLAUDE.md` that just lists files without explaining why they exist
- An ADR that says "we decided to use X" without saying why X over Y
- Comments that paraphrase code: `// increment count`
- Long-form prose in a function body comment — extract to a CLAUDE.md if it's general; remove if it's incidental
