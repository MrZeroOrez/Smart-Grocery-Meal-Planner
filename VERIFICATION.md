# Verification record

- Clean Maven package: 10 JUnit tests passed; no failures/errors/skips. Auth HTTP tests cover private API access, validation, duplicate registration, login and logout. Domain tests cover the business workflows, ownership, failed-operation rollback and concurrent stock/coupon limits.
- Strict TypeScript check and Vite production React build: PASS.
- Chromium browser tests against executable Spring Boot JAR, Vite proxy and file-based H2: PASS. Customer and administrator flows exercised through real API requests. No uncaught browser JavaScript errors.
- 390px mobile viewport: no page-wide horizontal overflow in tested screens.
- Backend stop/start against the same file-based database: PASS; stored catalogue/campaign data retained.
- Desktop UI screenshots inspected.

Not verified here: Windows CMD/PowerShell launchers, macOS, Docker image build, actual PostgreSQL, cloud deployment, dependency vulnerability scan, production load/security audit or live external integrations. CI configuration is included but GitHub run status is separate from this local verification.

End-to-end flow is local UI -> authenticated API -> persistent SQL. Real payments are simulated. Grocery and loyalty are independent apps; no cross-app event bridge is claimed.
