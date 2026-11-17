# Technical / Design Document

## 1. Goal and scope

A small, production-minded service to **create, view, update, delete, search and filter candidates**, with
persistence, validation, error handling and automated tests. The brief leaves architecture open, so the
priority was a simple, conventional, easy-to-explain design that can be evolved (section 9).

## 2. Technology choices

| Choice                                                       | Why |
|--------------------------------------------------------------|---|
| Java 25, Spring Boot 4.0.9 (Spring Framework 7, Hibernate 7) | Requested stack; current Boot 4.0.x patch release. |
| Spring MVC (not WebFlux)                                     | Blocking JPA/JDBC workload; MVC is simpler and virtual threads are available later. |
| Spring Data JPA + Specifications                             | Dynamic multi-criteria search without hand-built SQL strings. |
| Flyway                                                       | Versioned, reviewable schema; the same migrations run in tests and production. Hibernate does not own the schema (`ddl-auto=none`). |
| H2 (file) by default, PostgreSQL profile                     | Reviewer can run it with zero setup and still get persistence; PostgreSQL is the intended production database. |
| Bean Validation + RFC 9457 problem details                   | Declarative validation and a consistent, machine-readable error contract. |
| No Lombok, no MapStruct                                      | Fewer moving parts; records for DTOs and a 40-line mapper are enough. |

## 3. Architecture

Classic layered design, grouped by feature:

```
HTTP -> RequestIdFilter -> CandidateController -> CandidateService -> CandidateRepository -> DB
                                  |                     |
                              (DTOs, validation)   (rules, transactions, mapping)
                         GlobalExceptionHandler  (all errors -> problem+json)
```

* **Controller** - HTTP only: binds/validates input, builds `Location`, no business logic.
* **Service** - business rules (unique email, optimistic-lock check, range and sort validation), transaction
  boundaries (`readOnly` by default, write methods opt in) and entity -> DTO mapping *inside* the transaction
  (`open-in-view` is disabled, so lazy loading can never leak into the web layer).
* **Repository** - Spring Data JPA; `JpaSpecificationExecutor` for search.
* Entities are never exposed over the API; request and response are separate records.

## 4. Data model

`candidates` (`id` PK identity, `first_name`, `last_name`, `email` UNIQUE, `phone`, `current_title`,
`current_company`, `location`, `years_of_experience` 0-60 CHECK, `status`, `version`, `created_at`, `updated_at`)
and `candidate_skills` (`candidate_id` FK ON DELETE CASCADE, `skill`, PK `(candidate_id, skill)`).

Decisions:

* **Skills in a child table**, not a delimited string or JSON column: it is relational, indexable
  (`idx_candidate_skills_skill`) and gives exact, duplicate-free "has all of these skills" filtering.
* **Normalisation on write**: email and skills are lower-cased/trimmed, so uniqueness and matching are
  deterministic and case-insensitive without functional indexes.
* **Status is an enum stored as text** (never ordinal) so reordering code cannot corrupt data.
* **Constraints live in the database as well as in validation** (unique email, NOT NULLs, experience check),
  so the DB protects itself against any other writer.
* **`version` column** for optimistic locking; **`created_at`/`updated_at`** audit timestamps (UTC `Instant`).
* Indexes on the filter/sort columns: `status`, `location`, `years_of_experience`, `created_at`, `skill`.

## 5. API design

REST, versioned path (`/api/v1`), plural noun, correct status codes (201 + `Location`, 204, 400, 404, 409).
Details and examples are in the README.

* **PUT = full replace** of the representation (idempotent). Chosen over PATCH to keep semantics unambiguous;
  `status` is optional so a client can omit it without resetting the pipeline stage.
* **Lost-update protection**: responses carry `version`; if a PUT includes `version` and it is stale the API
  returns `409`. (JPA `@Version` additionally guards concurrent transactions.)
* **Search is a `GET` on the collection** with query parameters; repeated params for multi-values
  (`status=A&status=B`, `skills=java&skills=kafka`). Semantics: AND across filters, OR within `status`, AND within
  `skills`.
* **Pagination always on**: default size 20, hard cap 100, stable ordering (`id` is appended as tie-breaker),
  and a custom `PageResponse` so the JSON shape does not depend on Spring internals.
* **Sort whitelist**: clients cannot sort by arbitrary properties.

## 6. Search and filtering

`CandidateSpecifications` turns `CandidateSearchCriteria` into a JPA `Specification`:

* Free text `q`: `lower(col) LIKE %q% ESCAPE '\'` over first name, last name, full name, email, title, company.
* `%`, `_` and `\` in user input are escaped, so users cannot craft expensive/unintended patterns and
  searching for `100%` works literally.
* Skills: one `MEMBER OF` (EXISTS sub-query) per skill, so there is no join fan-out and no `DISTINCT` needed,
  which keeps paging and `count` queries correct.
* Everything is parameterised through the Criteria API - there is no string-built SQL (SQL-injection safe).
* The skills collection is `@BatchSize`-fetched, so listing a page of N candidates costs a constant number of
  queries instead of N+1.

Known limit: `LIKE '%x%'` cannot use a B-tree index, which is fine for tens of thousands of rows and is
addressed in the scaling plan.

## 7. Error handling, security, logging

**Errors.** One `@RestControllerAdvice` returns `application/problem+json` for everything:
field-level validation errors (`errors` map), malformed JSON, bad enum / type, unknown id, duplicate email,
stale version, unsupported method, unknown path. Unexpected errors return a generic 500 and are logged with a
stack trace; internals are never returned (`server.error.include-*` are off too). A DB unique-constraint
violation from a race between two concurrent inserts is also mapped to 409.

**Security (what is done).** Parameterised queries only; strict bean validation and size limits on every
field; page-size cap and sort whitelist (cheap DoS/abuse limits); wildcard escaping; no stack traces or SQL in
responses; request-id header accepted only if it matches `[A-Za-z0-9-]{1,64}` (log-injection guard); no PII
(emails, phones) in log lines - only ids.
**Not done (deliberately out of scope for the exercise):** authentication/authorisation, rate limiting, TLS,
encryption at rest. They would be added at the edge/gateway plus Spring Security with OAuth2/OIDC JWT and
role-based access (recruiter vs read-only); candidate data is personal data, so this is the first thing to add
before real use, together with audit logging and a retention/erasure policy.

**Logging.** SLF4J; every request gets a correlation id (`X-Request-Id`, generated if absent) placed in the MDC,
echoed on the response and included in every log line; one access line per request (method, path, status,
duration); INFO for create/update/delete with ids; WARN for integrity conflicts; ERROR with stack trace for
unexpected failures. Actuator exposes `health` and `info`.

## 8. Testing approach

Layered pyramid, all run by `mvn verify` (see README table): fast unit tests for mapping and business rules
(Mockito, no Spring), integration tests against real Hibernate + the real Flyway schema for query correctness
(the part most likely to be wrong), and full-stack MockMvc tests for the HTTP contract and error paths.
Tests use an in-memory H2 under the `test` profile.
Gaps I would close next: Testcontainers PostgreSQL (to remove the H2-vs-Postgres dialect risk), concurrency
tests for the optimistic-lock/unique-email races, and load tests (k6/Gatling).

## 9. Additional requirement: evolving the solution for much larger scale

I would grow the system in stages, driven by measurements, because each stage adds operational cost.

**Stage 1 - Scale the monolith (cheap, do first).**
* The app is already stateless, so run N replicas behind a load balancer (Kubernetes HPA on CPU/latency);
  enable virtual threads (`spring.threads.virtual.enabled=true`) for I/O-bound concurrency.
* Move to PostgreSQL with PgBouncer / a sized Hikari pool; add a **read replica** and route read-only
  transactions (search/view) to it. Tune with `EXPLAIN ANALYZE`, `pg_stat_statements`.
* Replace offset pagination with **keyset (seek) pagination** (`WHERE (created_at,id) < (?,?)`) for deep pages
  and make the exact total optional - `COUNT(*)` over large filtered sets becomes the bottleneck.
* Composite indexes matched to real filter combinations (e.g. `(status, created_at)`), partial indexes for
  active candidates.
* Observability: Micrometer metrics + Prometheus/Grafana, OpenTelemetry tracing, structured JSON logs shipped
  to a central store, SLO alerts on p95 latency and error rate.

**Stage 2 - Fix search properly.**
* Interim: PostgreSQL `pg_trgm` GIN indexes (fast `LIKE '%x%'`) or `tsvector` full-text search.
* Then a dedicated search engine (**OpenSearch/Elasticsearch**) for relevance ranking, typo tolerance, facets
  and skills/experience filters at scale. PostgreSQL stays the **source of truth**; the index is fed by
  change-data-capture (Debezium) or a transactional **outbox** publishing to Kafka, so writes are never lost
  and the index is rebuildable. The search endpoint contract stays the same.

**Stage 3 - Multi-tenancy, caching, async.**
* Add `tenant_id` to every table and to every index prefix; enforce with Postgres row-level security and
  derive the tenant from the JWT, never from the request.
* Redis cache for hot single-candidate reads and reference data, with ETag/`If-None-Match` using `version`
  for cheap HTTP caching; invalidate on write events.
* Move slow or bursty work off the request path via Kafka: bulk CSV/resume imports (JDBC batch inserts,
  idempotent consumers), notifications, indexing, audit trail. Rate limiting and WAF at the API gateway.

**Stage 4 - Data growth and organisation.**
* Partition large tables (by tenant hash or created-at range); archive/soft-delete old candidates and apply
  retention rules (GDPR erasure workflow).
* If one team/domain outgrows the monolith, split out *search* and *ingestion* first (clear boundaries and
  different scaling profiles) rather than going to microservices wholesale; use event contracts between them.
* Zero-downtime schema changes with the expand/contract pattern in Flyway; blue/green or canary releases;
  multi-AZ database with automated failover and tested backups (RPO/RTO defined).

**Trade-offs I would call out:** eventual consistency between DB and search index; operational overhead of
Kafka/OpenSearch; keyset pagination loses "jump to page N". I would only pay these when metrics show the
simpler stage is saturated.

## 10. Known limitations / trade-offs

* No authentication (see section 7); hard delete rather than soft delete; no audit history.
* Substring search is not relevance-ranked; skills are exact-match (after lower-casing), no synonyms.
* Verified-only-by-tests on H2; PostgreSQL behaviour should be confirmed with Testcontainers.
* `PUT` replaces all fields; a `PATCH` endpoint would be a natural addition.
