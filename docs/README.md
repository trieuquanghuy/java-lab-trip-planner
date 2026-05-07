# Trip Planner — SDLC Documentation

Practice/portfolio project: a destination discovery + day-by-day trip planning
web app with email-verified accounts, drag-and-drop itinerary scheduling, and
a map view of saved trips.

- **Stack**: Java 21 · Spring Boot 3 · React 18 + Vite + TypeScript · PostgreSQL 16 · Redis 7 · Docker Compose
- **Architecture**: Monorepo + microservices (api-gateway, auth-service, trip-service, destination-service)
- **External providers**: OpenTripMap (POIs), Foursquare (enrichment), GeoNames (city seed)
- **Status**: Design phase — awaiting review before implementation planning

## Document index

| # | Document | Purpose |
|---|----------|---------|
| 01 | [Product Requirements (PRD)](./01-prd.md) | Vision, personas, scope, numbered functional + non-functional requirements with traceability to source feature list |
| 02 | [System Architecture (HLD)](./02-architecture.md) | Service decomposition, tech stack rationale, repo layout, inter-service communication |
| 03 | [Data Model](./03-data-model.md) | ERD per service, table DDL, indexes, Flyway migration strategy |
| 04 | [API Specification](./04-api-spec.md) | Endpoint catalog per service, request/response shapes, error contract |
| 05 | [Auth & Security Design](./05-auth-security.md) | JWT flow across services, email verification, threat model, OWASP coverage |
| 06 | [Frontend Design](./06-frontend-design.md) | React app architecture, state management, routing, key flow sequences |
| 07 | [Test Strategy](./07-test-strategy.md) | Test pyramid, tooling, coverage targets per layer |
| 08 | [Deployment & DevOps](./08-deployment.md) | Local dev (Docker Compose), CI/CD pipeline, cloud deployment path |
| 09 | [Roadmap](./09-roadmap.md) | 11 sequenced phases with deliverables, acceptance, and demo points; v2 backlog |
| 10 | [Risk Register](./10-risks.md) | Identified risks with likelihood/impact and mitigations |

## How to read these documents

- Reviewers new to the project: read 01 → 02 → 09 first to understand *what* and *when*.
- Engineers about to implement: read 02 → 03 → 04 → 06 → 07 for build-ready detail.
- Security reviewers: read 05 first.
- Product reviewers: 01 + 09.

## Source

Original feature requirements: `Trip Planner Feature List.pdf` (in repo root). All
PRD requirements are mapped to the source user stories where applicable; new
requirements added during design are explicitly tagged.
