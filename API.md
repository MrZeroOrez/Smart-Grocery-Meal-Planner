# API reference

Private JSON APIs use `/api/v1`, `Authorization: Bearer <accessToken>`. Auth: POST `/auth/register` or `/auth/login` with `{"email":"...","password":"..."}`; GET `/auth/me`; POST `/auth/logout`. Only register/login and the root/static assets/health are public. Health is `/actuator/health` outside `/api/v1`.

400: invalid input; 401: missing/expired token; 403: admin permission missing; 404: absent/other-owner private resource; 409: business conflict, duplicate email, insufficient stock/points or idempotency conflict. Public registration always creates CUSTOMER; environment-configured bootstrap creates ADMIN. Admin tokens include CUSTOMER and ADMIN roles.

## Grocery

- GET `/products?q=rice&category=PANTRY`: catalogue. Query parameters optional.
- GET/PUT `/preferences`; PUT body `{"diet":"VEGAN"}`.
- GET `/cart`; PUT `/cart/{productId}` with `{"quantity":2}` (zero removes).
- GET `/products/{id}/substitutes`; POST `/cart/{from}/substitute/{to}`.
- GET `/recipes` (diet filtered).
- GET `/plan?week=2026-11-02`; PUT `/plan` with `{"date":"2026-11-02","slot":"LUNCH","recipeId":"chickpea-rice","servings":2}`.
- DELETE `/plan?date=2026-11-02&slot=LUNCH`.
- POST `/plan/cart`, body `{"week":"2026-11-02"}`, header `Idempotency-Key`.
- POST `/reservations`, no body, header `Idempotency-Key`. Returns reserved order snapshot.
- POST `/orders/{id}/confirm` or `/orders/{id}/cancel`, no body.
- GET `/orders`, caller-owned order history.
- PUT `/admin/products/{id}/stock` with `{"stock":20}`.

Request key: 8-80 letters, digits, underscore or hyphen. Repeat reservation key returns its original order, including terminal status; it does not reserve the new cart. Repeated meal-import key requires an unchanged plan. Checkout does not accept a client price or total.
