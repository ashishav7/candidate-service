# Candidate Service

A REST API for managing and searching candidate information, built with **Java 21 + Spring Boot 4.0.9**
(Spring Framework 7, Hibernate 7, Flyway, Bean Validation, Actuator).

* Full CRUD, free-text search, multi-criteria filtering, sorting and pagination
* Input validation + consistent RFC 9457 `application/problem+json` errors
* Persistent storage (file-based H2 by default, PostgreSQL via a profile), schema managed by Flyway
* Unit + integration tests (`mvn verify`)
* Docs: [`docs/DESIGN.md`](docs/DESIGN.md) (design, trade-offs, scaling plan)

## Prerequisites

* JDK 25+
* Maven 3.9+ (no Maven wrapper is committed; run `mvn -N wrapper:wrapper` if you want one)
* Optional: Docker, only for the PostgreSQL profile

## Run

```bash
mvn spring-boot:run
```

The API is at `http://localhost:8080/api/v1/candidates`. Data is stored in `./data/candidates.mv.db`
and survives restarts. Health: `http://localhost:8080/actuator/health`.

### Run against PostgreSQL

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
# overrides: DB_URL, DB_USER, DB_PASSWORD, DB_POOL_SIZE
```

## Test

```bash
mvn verify
```

| Test class | Kind | What it proves |
|---|---|---|
| `CandidateMapperTest` | unit | normalisation of email / skills / blanks, status defaulting |
| `CandidateServiceTest` | unit (Mockito) | business rules: duplicate email, stale version, 404s, invalid ranges, sort whitelist |
| `CandidateSearchIntegrationTest` | integration (real Hibernate + Flyway + H2) | free-text, status OR, skills AND, ranges, combined filters, wildcard escaping, paging/sorting |
| `CandidateApiIntegrationTest` | integration (MockMvc, full stack) | CRUD lifecycle, 201/204/400/404/409 paths, validation payloads, page-size cap, request-id handling |

## API

Base path: `/api/v1/candidates`

| Method | Path | Description | Success |
|---|---|---|---|
| POST | `/` | Create a candidate | `201` + `Location` |
| GET | `/{id}` | View a candidate | `200` |
| GET | `/` | Search / filter / page / sort | `200` |
| PUT | `/{id}` | Replace a candidate (optional `version` for optimistic locking) | `200` |
| DELETE | `/{id}` | Delete a candidate | `204` |

Errors: `400` validation / bad params, `404` unknown id, `409` duplicate email or stale version, `500` unexpected
(generic body, details only in logs).

### Candidate fields

| Field | Rules |
|---|---|
| `firstName`, `lastName` | required, max 100 |
| `email` | required, valid, max 255, unique (case-insensitive) |
| `phone` | optional, `+`, digits, spaces, `()`, `-`, 7-20 chars |
| `currentTitle`, `currentCompany`, `location` | optional, max 150 |
| `yearsOfExperience` | required, 0-60 |
| `status` | `NEW` (default) `SCREENING` `INTERVIEWING` `OFFERED` `HIRED` `REJECTED` |
| `skills` | optional set, max 50 entries, each max 50 chars (stored lower-case) |
| `version` | read-only on responses; optional on PUT |

### Search parameters (`GET /api/v1/candidates`)

All optional, combined with **AND**.

| Param | Meaning |
|---|---|
| `q` | case-insensitive "contains" over first/last/full name, email, title, company |
| `status` (repeatable) | candidate status is **any** of the given values |
| `skills` (repeatable) | candidate has **all** the given skills |
| `location`, `company` | case-insensitive "contains" |
| `minExperience`, `maxExperience` | inclusive range, years |
| `page` (0-based), `size` (default 20, max 100) | paging |
| `sort` | `field,asc|desc`; allowed: `id firstName lastName email yearsOfExperience status createdAt updatedAt` (default `createdAt,desc`) |

### Examples

```bash
# create
curl -i -X POST localhost:8080/api/v1/candidates -H 'Content-Type: application/json' -d '{
  "firstName":"Jane","lastName":"Doe","email":"jane@acme.com","phone":"+91 98765 43210",
  "currentTitle":"Backend Engineer","currentCompany":"Acme","location":"Gurugram",
  "yearsOfExperience":5,"skills":["Java","Kafka"]}'

# view
curl localhost:8080/api/v1/candidates/1

# update (send the version you read to avoid overwriting someone else's change)
curl -X PUT localhost:8080/api/v1/candidates/1 -H 'Content-Type: application/json' -d '{
  "firstName":"Jane","lastName":"Doe","email":"jane@acme.com","yearsOfExperience":6,
  "status":"SCREENING","skills":["java","kafka","spring"],"version":0}'

# search + filter: Java+Kafka people in Gurugram with 3-8 years, NEW or SCREENING, most experienced first
curl 'localhost:8080/api/v1/candidates?skills=java&skills=kafka&location=gurugram&minExperience=3&maxExperience=8&status=NEW&status=SCREENING&sort=yearsOfExperience,desc'

# delete
curl -i -X DELETE localhost:8080/api/v1/candidates/1
```

Validation error example (`400`):

```json
{
  "type": "about:blank", "title": "Validation failed", "status": 400,
  "detail": "Request validation failed", "instance": "/api/v1/candidates",
  "errors": { "email": "must be a well-formed email address", "yearsOfExperience": "must be greater than or equal to 0" }
}
```

## Project layout

```
src/main/java/com/candidatehub
  CandidateServiceApplication.java
  candidate/   entity, repository, DTOs, mapper, specifications (dynamic search), service, controller
  common/      exceptions, RFC 9457 error handler, request-id/logging filter, PageResponse
src/main/resources
  application*.properties, db/migration/V1__create_candidates.sql
src/test/java  unit + integration tests
```
