# AI usage

**Tool:** Claude (Anthropic), used through the claude.ai chat interface with a sandboxed environment (shell, embedded PostgreSQL 16 and Node).

**What it was used for**
- Turning the challenge brief into a design (schema, API shape, business rules, concurrency approach).
- Generating the first version of the Spring Boot backend, Flyway migrations and seed data, the React frontend and its CSS, tests, Docker files and this documentation.
- Validating parts of the work in the sandbox: the SQL migrations, seed data, the summary-refresh SQL, search query and bulk-data script were executed against a real PostgreSQL 16;
  the frontend was built with Vite and its unit tests run with Vitest.

**What was not verified by the tool:** the Java backend and its tests could not be compiled in the sandbox (Maven Central was not reachable),
so they must be built and run with `mvn test` on a normal machine.

**Example of something corrected during the work**
The first idea for keeping a product's denormalised price summary correct was simply to recompute it (`UPDATE products ... FROM (aggregate)`)
after each listing write. Reviewing the concurrency requirement showed this is racy: two transactions editing *different* listings of the *same* product
each recompute from a snapshot that lacks the other's uncommitted change, and the last commit can leave stale numbers. The design was changed so every writer first
takes a row lock on the product (`SELECT ... FOR UPDATE`) and only then runs the refresh as a separate statement (new snapshot after the lock is granted); bulk
seller-status changes lock products in id order to avoid deadlocks. A related first attempt, `UPDATE ... FROM LATERAL (...)`, was dropped for a CTE-based
update after checking the syntax against PostgreSQL.
