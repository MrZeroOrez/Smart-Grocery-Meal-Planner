# Learn the implementation

1. Register a customer and sign in as an admin. In DevTools Network, trace UI -> bearer header -> controller -> domain service -> JdbcTemplate -> SQL response.
2. Read auth/LabStore, AuthController, TokenService, SecurityConfiguration and AdminBootstrap. Explain why registration cannot set its own role, why restart invalidates JWTs and why password hashes never go to React.
3. Read schema.sql and the domain service. Identify each aggregate owner and every `FOR UPDATE` transaction. Find the exact operation that must roll back on failure.
4. Run the domain tests. Explain concurrent checkout/coupon limits, idempotent retries, and stock/points invariants. Add an edge-case test before changing a business rule.
5. Read frontend/src/main.tsx: forms, state, role-based tabs, API helper, refresh and error states. TypeScript strict mode is enabled; the dynamic API-row adapter remains explicitly typed as a generic record and is an opportunity to replace with dedicated generated API interfaces.
6. Run browser acceptance tests and review the workflow against the database-backed app. Reload/sign in, then restart and sign in to confirm persistence.
7. Study Dockerfile, render.yaml and CI. Distinguish supplied configuration from a verified deployment. Upgrade schema initialization to versioned migrations before production use.

Interview exercise: describe one user journey, one failure case, one race condition, one authorization boundary and one deliberate scope tradeoff. Use actual tests and measured behavior; do not invent scale/performance claims.
