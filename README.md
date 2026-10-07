# Smartup24 — Java integration gateway

Smartup24 is a B2B marketplace and supplier-management platform for the Central Asian market. It connects
suppliers, retail clients and platform moderators: product catalogue, orders, returns, promotions and bonus
programmes, field visits of sales agents, and integrations with external systems (SmartupX, Regos, Iiko,
SalesDoctor, Dalion/1C, Uzum, Nurfin, the Central Bank exchange-rate feed, the tax committee legal-info
service, Yandex geocoder).

This repository contains the **Java part** of the platform, published for review purposes. It is a thin
Spring Boot gateway layer: HTTP forwarding, scheduled jobs and runtime services that call external APIs
on behalf of the core. All business logic of Smartup24 lives in Oracle PL/SQL packages on top of the
**Biruni** low-code framework (GreenWhite proprietary); the PL/SQL core, the web front-end and the mobile
applications are **not** part of this repository.

## Architecture in one picture

```
Browser / mobile app / partner system
        │  HTTPS
        ▼
Spring Boot WAR  (this repo: uz.greenwhite.sb.*)
  ├─ Biruni core (framework WAR overlay, proprietary — not included)
  ├─ REST controllers  → forward partner calls into the core (UzumServiceController)
  ├─ JobProviders      → scheduled pulls from external systems (currency rates, Iiko)
  └─ RuntimeServices   → outbound calls the PL/SQL core asks for (STC legal info,
                          SalesDoctor token, Nurfin webview launch, Yandex geocoder, Regos)
        │  JDBC
        ▼
Oracle Database — PL/SQL packages (business logic, routing, security) — not included
```

A request reaches a Biruni controller, which resolves the route in Oracle and executes the PL/SQL
procedure behind it. When the core needs to call the outside world (sign a request, fetch a token,
geocode a point), it schedules a *runtime service* implemented here; the Java side only transports
bytes and reports HTTP status — URLs, keys and parsing rules are owned by the PL/SQL side and are read
from the database at call time.

## What is in `src/main/java/uz/greenwhite/sb`

| Package / class | Role |
|---|---|
| `SbApplication` | Spring Boot entry point |
| `config.ApplicationConfig`, `config.HttpClientConfig` | Externalised settings, shared OkHttp client |
| `CurrencyJobProvider`, `CurrencyRateService` | Daily exchange-rate pull from the Central Bank of Uzbekistan public JSON feed |
| `iiko.IikoBalanceImportJobProvider`, `iiko.IikoOrderSyncJobProvider` | Stock import and invoice push for clients running the Iiko restaurant system |
| `regos.RequestService` | Regos POS integration transport |
| `salesdoctor.UpdateTokenRuntimeService` | Obtains a SalesDoctor API token for a supplier |
| `nurfin.NurfinLaunchRuntimeService` | Signs (HMAC-SHA256) and sends the Nurfin instalment-financing webview launch request |
| `STCLegalInfoRuntimeService` | Legal-entity lookup by tax number (Basic auth, credentials supplied per call) |
| `YandexGeocoderRuntimeService` | Reverse geocoding fetch; URL and key are built by the core |
| `uzum.*` | Gateway for the Uzum marketplace callbacks (`/uzum_service/check|create|confirm|reverse|status`) forwarded into the core |
| `util.AuthUtil` | Basic-auth header helper |

No credentials, hosts or keys are hard-coded: every integration reads its settings from the database
at runtime, so the same build serves every tenant.

## Building

The project is a Maven WAR overlay on top of the Biruni framework artifacts (`uz.greenwhite:biruni`,
`core` jar and framework `war`), which are published in a private GitHub Packages repository. Without
access to those artifacts the sources compile only against stubs of the Biruni interfaces
(`JobProvider`, `RuntimeService`, `RuntimeResult`); the repository is therefore intended for code review
rather than for a standalone build.

```
./mvnw clean package -P prod     # requires access to the Biruni Maven repository
```

Java 21, Spring Boot 3.x, Scala 2.13 (legacy, no Scala sources in this module).

## Not included on purpose

- Oracle PL/SQL packages, DDL and migrations (the business logic and data model of the platform)
- Biruni framework binaries and the web page definitions (`main/page`)
- Angular web front-end and Flutter mobile applications
- Any environment configuration, credentials, certificates or deployment scripts

## Copyright

© GreenWhite. All rights reserved. The code is published for evaluation purposes only; no licence to
use, copy or redistribute it is granted.
