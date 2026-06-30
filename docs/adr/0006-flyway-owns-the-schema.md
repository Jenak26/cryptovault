# 6. Flyway owns the schema; Hibernate runs `ddl-auto=validate`

**Status:** Accepted

## Context
Letting Hibernate manage the schema (`ddl-auto=update`) is convenient in a tutorial but dangerous in
anything real: it makes uncontrolled, unreviewable, environment-dependent changes, can't express data
migrations or seed data, and has no rollback story. A vault needs a deliberate, versioned schema.

## Decision
**Flyway owns the schema.** Every change is an ordered, immutable migration in
`src/main/resources/db/migration` (`V1…V6`). Hibernate runs with **`ddl-auto=validate`**, so on
startup it confirms the entities match the Flyway-built schema but **never modifies** it.

## Consequences
- Schema changes are explicit, reviewed in PRs, and applied identically in every environment and in CI.
- `validate` catches entity/schema drift at boot instead of at runtime.
- Seed data (roles) and structural changes live in the same versioned history.
- Slightly more friction: a new column means writing a migration **and** updating the entity — which
  is the point (intentional change, not accidental).

## Alternatives considered
- **`ddl-auto=update`** — rejected: unreviewable, no data migrations/seeds, no rollback, drifts
  between environments.
- **`ddl-auto=none` + manual SQL** — rejected: loses the boot-time validation that guards against
  entity/schema drift.
