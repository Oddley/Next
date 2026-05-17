# ADR-006: Git Workflow — Conventional Commits, Trunk-Based

**Status:** Accepted

## Context

Solo-with-AI-pair development. We want commit history that's readable months later, easy to bisect, and supports issue-driven planning.

## Decision

### Conventional Commits

Every commit message follows the format:

```
<type>(<scope>): <short description>

<optional body, explaining WHY not WHAT>

<optional footer, e.g. issue refs, breaking-change notes>
```

Types used in this project:

| Type | When |
|---|---|
| `feat` | New feature or user-visible behavior |
| `fix` | Bug fix |
| `refactor` | Code restructuring with no behavior change |
| `test` | Adding or modifying tests only |
| `docs` | Documentation only |
| `chore` | Build config, deps, tooling, non-functional changes |
| `style` | Formatting / whitespace only (rare; usually rolled into `refactor`) |

Scopes are folder-relative: `feat(domain/snooze)`, `fix(notification)`, `docs(adr)`.

### Trunk-based, push frequently

- `main` is always shippable
- Push every commit that builds + tests pass; don't accumulate large local stacks
- Branches are for in-flight risky changes; merge fast or kill them

### Issue references

Reference GitHub Issues in commit footers when the commit closes or makes progress on one:

```
feat(domain/snooze): apply-snooze increments offset on existing session

Closes #12
```

### No squash on merge

Preserve the granular history. A feature implemented across 5 commits stays as 5 commits in `main` — easier to bisect, easier to understand the path that was taken.

## Rationale

- **Conventional Commits** make `git log --oneline` immediately scannable. Tooling (changelog generators, semantic-release) can consume them if we ever want.
- **Trunk-based** matches the team size (one + AI). Branching overhead is wasted ceremony.
- **Granular history** preserves the iterative thinking; squashing erases the journey, which is most of the educational value of looking at old code.

## Consequences

- Commit messages take ~30 seconds longer to write. Worth it.
- History has more entries; `git log` requires `--oneline` to be readable. Fine.
- No automatic changelog from squashed PRs — if we want one, generate it from Conventional Commits across the date range.

## Anti-patterns

- "WIP" commits in main — squash or rebase before push if they ever happen
- Commit messages that describe the code change ("changed foo to bar") rather than the intent ("fix snooze decrement when session expired")
- Long-lived feature branches — if a feature is too big for a few commits, break it into shippable phases per ADR / plan.md

## Working with Claude

Claude should:
1. Run tests + build before suggesting a commit
2. Draft a Conventional Commit message focused on the WHY
3. Show the message to the human for review before running `git commit`
4. After commit succeeds, push to origin as a standalone command (no chaining)
5. Reference relevant GitHub Issues in the footer when applicable
