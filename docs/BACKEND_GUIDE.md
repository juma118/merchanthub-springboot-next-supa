# MerchantHub Backend — A Learning Guide

A guided tour of the Spring Boot backend, written to **teach**. It explains what each
file does *and* the Spring/Java concepts behind it, in the order that makes them easiest
to learn. Read top-to-bottom the first time; later use it as a reference.

All paths are under [`backend/`](../backend). Package root is `com.merchanthub`.

---

## Table of contents

1. [The 30-second mental model](#1-the-30-second-mental-model)
2. [How a request flows through the app](#2-how-a-request-flows-through-the-app)
3. [Project setup: `pom.xml` and `application.yml`](#3-project-setup)
4. [Bootstrapping: `MerchantHubApplication`](#4-bootstrapping)
5. [Configuration layer (`config/`)](#5-configuration-layer)
6. [Security & authentication (`security/`)](#6-security--authentication)
7. [Multi-tenancy: the heart of the app (`tenant/`)](#7-multi-tenancy)
8. [Persistence: entities, repositories, migrations](#8-persistence)
9. [DTOs (`dto/`)](#9-dtos)
10. [Services — the business logic (`service/`)](#10-services)
11. [Controllers — the REST layer (`web/`)](#11-controllers)
12. [Error handling (`web/error/`)](#12-error-handling)
13. [Integration & scheduling](#13-integration--scheduling)
14. [Tests](#14-tests)
15. [Cross-cutting concepts glossary](#15-glossary)
16. [Suggested learning path & exercises](#16-learning-path)

---

## 1. The 30-second mental model

Spring Boot apps are built from **beans** — objects that Spring creates and wires together
for you. You annotate a class (`@Service`, `@Component`, `@RestController`…), and at startup
Spring:

1. **Scans** your packages for annotated classes,
2. **Instantiates** them (creating a "bean" for each),
3. **Injects** the beans they depend on (via constructors),
4. Wires in framework machinery (web server, JDBC pool, transactions, security filters).

This is **Inversion of Control / Dependency Injection (DI)**: you never call `new ProductService(...)`
yourself — you declare what you need in a constructor, and Spring supplies it. The benefit:
classes stay focused and are trivially testable (you can pass fakes in tests).

The backend is organized in classic layers:

```
HTTP  →  Controller (web/)  →  Service (service/)  →  Repository (repo/)  →  Postgres
              DTOs in/out         business logic        JPA queries          (with RLS)
```

Plus cross-cutting concerns that apply to *every* request: **security** (who are you?),
**multi-tenancy** (which merchant's data?), **transactions** (commit/rollback), and
**error handling** (turn exceptions into clean JSON).

---

## 2. How a request flows through the app

Trace `GET /api/products?q=tee` from an authenticated merchant. Keep this picture in mind —
every section below is one stop on this journey:

```
1. Tomcat (embedded web server) receives the HTTP request.
2. Security filter chain runs:
   └─ JwtAuthFilter validates the Bearer JWT, resolves the merchant,
      stores it in SecurityContext AND TenantContext (a thread-local).
3. Spring MVC routes to ProductController.list(q, page, size).
4. Controller calls productService.list(...).
5. @Transactional opens a DB transaction (outermost advice).
   └─ TenantIsolationAspect runs FIRST inside the tx:
      SET LOCAL app.current_merchant_id = '<this merchant>'
6. ProductRepository.search(...) runs a SQL query.
   └─ Postgres RLS policy silently adds: AND merchant_id = current setting
7. Rows come back → Service maps entities → DTOs.
8. Controller returns the DTO; Spring serializes it to JSON (Jackson).
9. Transaction commits; JwtAuthFilter clears the thread-locals in `finally`.
```

The two "magic" steps — 5 and 6 — are the multi-tenancy mechanism. We'll build up to them.

---

## 3. Project setup

### `pom.xml` — the build file (Maven)

Maven is the build tool; `pom.xml` declares dependencies and how to package the app.
Key parts:

- **Parent** `spring-boot-starter-parent` — gives you a curated set of dependency *versions*
  that are known to work together (so you rarely specify versions yourself).
- **Starters** — a "starter" is a bundle of related dependencies. We use:
  - `spring-boot-starter-web` — Spring MVC + an embedded Tomcat server (REST APIs).
  - `spring-boot-starter-data-jpa` — JPA/Hibernate (object↔table mapping) + connection pool.
  - `spring-boot-starter-security` — authentication/authorization filter chain.
  - `spring-boot-starter-validation` — `@Valid`, `@NotBlank`, etc.
  - `spring-boot-starter-actuator` — health endpoints (`/actuator/health`).
  - `flyway-core` + `flyway-database-postgresql` — versioned SQL migrations.
  - `postgresql` — the JDBC driver.
  - `nimbus-jose-jwt` — verify/mint JWTs (we don't use the full OAuth2 stack; just the JWT lib).
  - `springdoc-openapi-...` — auto-generates Swagger UI from the controllers.
  - `lombok` — generates getters/setters at compile time (`@Getter/@Setter`) to cut boilerplate.
  - test: `spring-boot-starter-test`, `testcontainers` — integration tests against real Postgres.
- **`spring-boot-maven-plugin`** — packages everything into one runnable "fat JAR"
  (`java -jar app.jar`).

> **Concept — fat JAR:** Spring Boot bundles your code *and* all dependencies *and* an
> embedded Tomcat into a single executable JAR. No external app server needed. The
> [`Dockerfile`](../backend/Dockerfile) builds this JAR in one stage and runs it in a slim JRE image.

### `src/main/resources/application.yml` — configuration

This is where you configure Spring without code. Highlights and the *why*:

- **Two datasources.** This is unusual and important:
  - `spring.datasource.*` → the **runtime** connection, as the restricted role `merchanthub_app`
    (no superuser, no `BYPASSRLS`). All normal queries use this, so RLS applies to them.
  - `spring.flyway.*` → a separate **admin** connection (superuser `postgres`) used only to
    run migrations (creating tables, RLS policies, functions, seed data — things the
    restricted role isn't allowed to do).
- `spring.jpa.hibernate.ddl-auto: none` — Hibernate must **not** create/alter tables.
  Flyway owns the schema. (Letting Hibernate auto-generate schema is convenient in toys but
  dangerous in real apps; migrations are the professional approach.)
- `merchanthub.*` — our own custom settings (JWT secret, webhook secret, sync interval…),
  read by `AppProperties` (see §5).
- `${ENV_VAR:default}` syntax — values come from environment variables with a fallback,
  so the same build runs locally and in Docker with different config.

> **Concept — externalized config:** code reads settings from `application.yml`, which reads
> from environment variables. You never hardcode secrets or URLs. `docker-compose.yml`
> supplies the env vars for the container.

---

## 4. Bootstrapping

### `MerchantHubApplication.java`

```java
@SpringBootApplication
public class MerchantHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(MerchantHubApplication.class, args);
    }
}
```

`@SpringBootApplication` is three annotations in one:
- `@SpringBootConfiguration` — this class can define beans.
- `@EnableAutoConfiguration` — Spring Boot looks at what's on the classpath and auto-configures
  it (sees `spring-boot-starter-web` → starts Tomcat; sees a JDBC driver → builds a DataSource).
- `@ComponentScan` — scans `com.merchanthub` and sub-packages for `@Component`/`@Service`/
  `@RestController`/etc. and registers them as beans.

`SpringApplication.run(...)` starts the whole thing: builds the application context (the
container of all beans), starts Tomcat, runs Flyway, and blocks until shutdown.

---

## 5. Configuration layer

Files in `config/` customize framework behavior.

### `AppProperties.java` — typed configuration

```java
@Component
@ConfigurationProperties(prefix = "merchanthub")
public class AppProperties { private String jwtSecret; ... }
```

`@ConfigurationProperties(prefix = "merchanthub")` binds the `merchanthub.*` block of
`application.yml` onto this object's fields. Instead of sprinkling
`@Value("${merchanthub.jwt-secret}")` everywhere, you inject one typed `AppProperties` bean
and call `props.getJwtSecret()`. Cleaner and discoverable.

### `AppConfig.java` — core wiring & the transaction ordering trick

```java
@Configuration
@EnableScheduling
@EnableAspectJAutoProxy
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class AppConfig {
    @Bean RestClient.Builder restClientBuilder() { return RestClient.builder(); }
}
```

- `@Configuration` — a class that defines beans via `@Bean` methods.
- `@Bean` — the method's return value becomes a managed bean (here, an HTTP client builder
  used by `ShopApiClient`).
- `@EnableScheduling` — turns on support for scheduled tasks (used by the sync scheduler).
- `@EnableAspectJAutoProxy` — turns on AOP (aspect) support (used by `TenantIsolationAspect`).
- `@EnableTransactionManagement(order = HIGHEST_PRECEDENCE)` — turns on `@Transactional`,
  **and pins its advice to the outermost position**. This is subtle but crucial: it lets
  our tenant aspect run *inside* the transaction. Full explanation in §7.

> **Concept — AOP (Aspect-Oriented Programming):** a way to run cross-cutting code "around"
> many methods without editing them. `@Transactional` itself is AOP: Spring wraps your bean
> in a **proxy** that opens a transaction before your method and commits/rolls back after.
> We add a second aspect for tenant scoping.

### `WebConfig.java` — CORS

Defines a `CorsConfigurationSource` bean allowing the browser dashboard (a different origin,
`localhost:3000`) to call the API (`localhost:8080`). Spring Security auto-detects this bean.
Without CORS config, browsers block cross-origin requests.

### `OpenApiConfig.java` — API docs

Adds title/version metadata for the auto-generated Swagger UI at `/swagger-ui.html`.
springdoc inspects your controllers and produces interactive docs for free.

---

## 6. Security & authentication

The job: every `/api/**` request (except a few public ones) must carry a valid **JWT**
(JSON Web Token); from it we learn *which merchant* is calling.

> **Concept — JWT:** a signed token the client sends in `Authorization: Bearer <token>`.
> It contains claims (e.g. `sub` = user id, `email`). Because it's signed with a secret,
> the server can trust it without a database session. Supabase issues these on login; we
> validate them with the shared `SUPABASE_JWT_SECRET` (HS256 algorithm).

### `JwtService.java` — validate and mint tokens

Two methods:
- `validate(token)` → checks the signature and expiry using the secret, returns the `sub`
  (auth user id) and `email`. Throws if invalid/expired.
- `mint(sub, email, ttl)` → creates a signed token. Used **only** by the dev-login endpoint
  so you can run the app without a real Supabase account.

Uses the Nimbus JOSE library directly (`MACSigner`/`MACVerifier` = HMAC for HS256).

### `MerchantPrincipal.java`

A small `record` holding the authenticated identity: `merchantId`, `authUserId`, `email`.
This becomes Spring Security's "principal" (the current user).

> **Concept — `record`:** a concise immutable data class (Java 17+). `record Foo(int a)`
> auto-generates the constructor, `a()` accessor, `equals`, `hashCode`, `toString`. We use
> records everywhere for DTOs and value objects.

### `JwtAuthFilter.java` — the gatekeeper (runs on every request)

A `OncePerRequestFilter` (servlet filter guaranteed to run once per request). Logic:

1. No `Authorization: Bearer` header? Pass through (public endpoints handle themselves;
   protected ones get rejected later).
2. Validate the token via `JwtService`. Invalid → respond `401` immediately.
3. Resolve the merchant from the token's `sub` (via `MerchantResolver`, §7). If this auth
   user has no merchant yet, **auto-provision** one (first-login onboarding).
4. Put a `MerchantPrincipal` into Spring Security's `SecurityContext` (so the request counts
   as authenticated) **and** the merchant id into `TenantContext` (so queries get scoped).
5. `chain.doFilter(...)` — continue to the controller.
6. In a `finally` block, **clear both thread-locals**. Critical: threads are reused across
   requests; forgetting to clear would leak one user's tenant into the next request.

> **Note:** this filter is *not* a Spring bean (no `@Component`). Why? Spring Boot
> auto-registers any filter bean into the servlet chain, which would make it run twice
> (once standalone, once in the security chain). We construct it directly in `SecurityConfig`
> to avoid the double registration — a real bug we hit and fixed.

### `SecurityConfig.java` — the rules

```java
@Configuration
public class SecurityConfig {
  @Bean SecurityFilterChain filterChain(HttpSecurity http) { ... }
}
```

Defines the `SecurityFilterChain` bean that configures:
- **CSRF disabled** — we're a stateless JWT API, not a cookie/session app, so CSRF doesn't apply.
- **Stateless sessions** — no server-side session; identity comes from the token each time.
- **Authorization rules** — `permitAll()` for `/api/auth/**`, `/api/webhooks/**`, health, and
  Swagger; `authenticated()` for everything else under `/api/**`.
- Registers `JwtAuthFilter` *before* the username/password filter.
- A custom 401 JSON entry point for unauthenticated access.

---

## 7. Multi-tenancy

This is the project's centerpiece: **two independent layers** ensure one merchant can never
see another's data. Three small files implement it.

### `TenantContext.java` — "who is the current tenant?"

```java
public final class TenantContext {
  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
  public static void setMerchantId(UUID id) { CURRENT.set(id); }
  public static UUID getMerchantId() { return CURRENT.get(); }
  public static void clear() { CURRENT.remove(); }
}
```

> **Concept — `ThreadLocal`:** each thread gets its own copy of the value. A web request is
> handled by one thread start-to-finish, so a `ThreadLocal` is a clean way to carry "the
> current merchant" through all the layers without passing it as a parameter to every method.
> (And why clearing it in the filter's `finally` is mandatory — see above.)

It's populated by:
- `JwtAuthFilter` for API requests,
- `WebhookService` after resolving the merchant by API key (webhooks have no JWT),
- `SyncScheduler` for each merchant in the background job (no request thread at all).

### `MerchantResolver.java` — lookups *before* a tenant exists

There's a chicken-and-egg problem: to scope queries to a merchant you need the merchant id,
but to find the merchant (by auth id, email, or API key) you must read the `merchants` table
*before* any scope is set — and that table has RLS too.

Solution: these specific lookups call Postgres **`SECURITY DEFINER` functions**
(`resolve_merchant_by_auth_uid`, `…_by_email`, `…_by_api_key`, `provision_merchant`) defined
in the V2 migration. A `SECURITY DEFINER` function runs with the *definer's* (superuser's)
privileges, bypassing RLS — the only sanctioned escape hatch. `MerchantResolver` calls them
with a plain `JdbcTemplate` (raw SQL, no entity mapping).

> **Concept — `JdbcTemplate`:** Spring's thin wrapper over JDBC for running SQL directly and
> mapping rows to objects. We use JPA for normal entities but drop to `JdbcTemplate` for these
> function calls and for hand-tuned analytics SQL.

### `TenantIsolationAspect.java` — the database safety net

```java
@Aspect @Component @Order(Ordered.LOWEST_PRECEDENCE)
public class TenantIsolationAspect {
  @PersistenceContext EntityManager em;

  @Before("@annotation(org.springframework.transaction.annotation.Transactional) "
        + "&& execution(* com.merchanthub..*(..))")
  public void applyTenantScope() {
    UUID id = TenantContext.getMerchantId();
    if (id != null)
      em.createNativeQuery("select set_config('app.current_merchant_id', :id, true)")
        .setParameter("id", id.toString()).getSingleResult();
  }
}
```

What it does: **before any `@Transactional` method runs**, it pushes the current merchant id
into a Postgres session variable. The RLS policies (defined in the database) then read that
variable and automatically filter every query to that merchant — even if the Java code forgot
its `WHERE merchant_id = ?`.

The **ordering** is the clever part and the reason for `@EnableTransactionManagement(order =
HIGHEST_PRECEDENCE)` back in `AppConfig`:

```
Call productService.list()  ─┐
  [transaction advice]  ← HIGHEST_PRECEDENCE = OUTERMOST: opens the DB transaction
    [tenant aspect]     ← LOWEST_PRECEDENCE  = INNERMOST: runs SET LOCAL inside the tx
      your method body  ← now every query is scoped by the session variable
    [tenant aspect ends]
  [transaction commits]
```

`SET LOCAL` only lasts for the current transaction, and the aspect must run *inside* it (on
the same DB connection). Making the transaction advice outermost guarantees that. The
`set_config(..., true)` "true" means *local to this transaction*, so the value resets when the
pooled connection is returned — no leakage between requests.

> **Why two layers?** The Java `WHERE merchant_id = ?` clauses (application layer) are the
> primary guard. The RLS policies (database layer) are a **safety net**: if a single query
> ever forgets to scope, the database still refuses to return another tenant's rows. Defense
> in depth — a bug in one layer can't leak data.

> **Concept — proxies & self-invocation gotcha:** AOP (`@Transactional`, aspects) works by
> wrapping a bean in a **proxy**. The proxy only intercepts calls that come *from outside* the
> bean. If method A calls method B in the *same* class, the proxy is bypassed and B's
> `@Transactional`/aspect won't fire. That's why `WebhookService` delegates persistence to a
> *separate* `WebhookPersistenceService` bean, and why the sync scheduler is a separate bean
> from `SyncService`. Remember this — it's a classic Spring trap.

---

## 8. Persistence

### The big picture

- **Flyway migrations** (`src/main/resources/db/migration/`) define the schema in versioned
  SQL files, run once at startup by the admin connection.
- **Entities** (`domain/`) are Java classes mapped to tables.
- **Repositories** (`repo/`) are interfaces that Spring Data turns into working query objects.

### Flyway migrations — the schema source of truth

Files run in version order, once each, tracked in a `flyway_schema_history` table:

- **`V1__init_schema.sql`** — creates tables (`merchants`, `products`, `inventory`, `orders`,
  `order_items`, `sync_logs`, `alerts`), indexes, constraints, and an `updated_at` trigger.
- **`V2__rls_policies.sql`** — enables **Row-Level Security** and adds a policy per table
  (`merchant_id = current_merchant_id()`), plus the `SECURITY DEFINER` resolver functions.
- **`V3__seed_demo.sql`** — inserts two demo merchants and ~780 orders so the analytics have
  real data. Uses `generate_series` + `random()` to fabricate a realistic history.

> **Concept — migrations vs auto-DDL:** never let an app silently alter your production
> schema. Migrations are explicit, reviewable, ordered, and repeatable across environments.
> Adding a column = adding a new `V4__...sql` file, never editing an old one.

> **Concept — RLS (Row-Level Security):** a Postgres feature where a table policy adds an
> implicit `WHERE` to every query for non-superuser roles. Our policy compares `merchant_id`
> to the session variable the aspect set. The app role can't see around it.

### Entities (`domain/`)

Plain classes annotated with JPA mappings. Example pattern:

```java
@Entity @Table(name = "products")
@Getter @Setter                       // Lombok generates accessors
public class Product {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "merchant_id", nullable = false) private UUID merchantId;
  @Column(nullable = false) private String sku;
  @Column(nullable = false) private BigDecimal price = BigDecimal.ZERO;
  @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
  @UpdateTimestamp  @Column(name = "updated_at") private Instant updatedAt;
}
```

- `@Entity` + `@Table` — maps the class to a table.
- `@Id` + `@GeneratedValue` — primary key (we generate UUIDs in the app).
- `@Column` — maps a field to a column (name, nullability).
- `@CreationTimestamp` / `@UpdateTimestamp` — Hibernate sets these automatically.
- `BigDecimal` for money (never `double` — floating point rounding corrupts currency).

The entities: `Merchant`, `Product`, `Inventory`, `OrderEntity` (named with the `Entity`
suffix because `Order` is a SQL keyword), `OrderItem`, `SyncLog`, `Alert`. Note `Alert.payload`
is a `Map<String,Object>` mapped to a Postgres `jsonb` column via `@JdbcTypeCode(SqlTypes.JSON)` —
Hibernate serializes the map to JSON automatically.

> **Design note — order items.** We chose *not* to model `Order`↔`OrderItem` as a JPA
> relationship (`@OneToMany`). Instead each is its own entity and we query items by `orderId`.
> Why: explicit control over queries (no surprise lazy-loading), and `order_items` carries its
> own `merchant_id` so RLS scopes it directly.

### Repositories (`repo/`)

```java
public interface ProductRepository extends JpaRepository<Product, UUID> {
  Optional<Product> findByIdAndMerchantId(UUID id, UUID merchantId);
  boolean existsByMerchantIdAndSku(UUID merchantId, String sku);

  @Query("""
      select p from Product p
      where p.merchantId = :mid
        and (:q = '' or lower(p.name) like lower(concat('%', :q, '%')))""")
  Page<Product> search(@Param("mid") UUID merchantId, @Param("q") String q, Pageable pageable);
}
```

> **Concept — Spring Data JPA:** you write an **interface**, Spring generates the
> implementation at runtime. Two ways to define queries:
> 1. **Derived queries** — Spring parses the *method name* (`findByIdAndMerchantId`) and writes
>    the SQL for you.
> 2. **`@Query`** — you write JPQL (or native SQL) for anything more complex.
>
> `JpaRepository<Product, UUID>` gives you `save`, `findById`, `delete`, paging, etc. for free.
> `Page`/`Pageable` handle pagination + total counts.

> **War story baked into the code:** the search uses `:q = ''` (empty-string sentinel), not
> `:q is null`. Postgres can't infer the type of a bound `NULL` inside `lower(... like ...)`
> and errors with `function lower(bytea) does not exist`. Passing `""` from the service
> sidesteps it. The order/funnel queries similarly use default bounds instead of `IS NULL`.

---

## 9. DTOs

`dto/` holds **Data Transfer Objects** — the shapes that cross the API boundary, kept separate
from entities.

> **Concept — why DTOs?** Entities are your internal database model; DTOs are your public API
> contract. Keeping them separate means you can change the DB without breaking clients (and
> vice-versa), avoid leaking internal fields, and shape responses exactly. They're all Java
> `record`s here (immutable, concise), e.g. `ProductDtos.ProductResponse`, grouped into holder
> classes by area (`ProductDtos`, `OrderDtos`, `AnalyticsDtos`, …).

`CommonDtos.PageResponse<T>` is a generic envelope `{ content, page, size, totalElements,
totalPages }` with a helper to map a `Page<Entity>` into a `PageResponse<Dto>`.

Validation lives on request DTOs: `@NotBlank`, `@PositiveOrZero`, etc. When a controller
param is `@Valid`, Spring checks these before your code runs and returns `400` on failure.

---

## 10. Services

`service/` is where the **business logic** lives. Controllers stay thin; services do the work
and own transactions.

> **Concept — `@Service` and `@Transactional`:** `@Service` marks a business-logic bean.
> `@Transactional` on a method means "run this in a database transaction": all its writes
> commit together, or roll back together if it throws. Read-only methods use
> `@Transactional(readOnly = true)`. Because of the tenant aspect (§7), `@Transactional` is
> *also* what triggers tenant scoping — so essentially every service method that touches the
> DB is transactional.

The services and what they teach:

| Service | Responsibility | Concepts to notice |
|---|---|---|
| `MerchantService` | `GET /me` — fetch the current merchant | simplest service; reads `TenantContext` |
| `ProductService` | product CRUD, CSV import/export | pagination, uniqueness checks, hand-rolled CSV parsing, upsert |
| `InventoryService` | stock levels, low-stock alerts | detecting a "threshold crossing" and emitting an alert |
| `OrderService` | list/detail of orders | filtering + paging; composing an order with its items |
| `OrderIngestionService` | turn an external order into rows | **idempotency** (skip if `external_id` exists), shared by webhook *and* sync |
| `AlertService` | create/list/mark-read alerts | a tiny service reused by many others |
| `AnalyticsService` | revenue, top products, funnel, forecast | raw aggregation SQL via `JdbcTemplate`; period-over-period math |
| `SyncService` | pull-sync a merchant from the shop API | calls external API, upserts catalog, ingests orders, writes a `sync_log` |
| `WebhookService` | verify + dispatch inbound webhooks | HMAC signature check; sets `TenantContext` manually |
| `WebhookPersistenceService` | transactional write for a webhook | exists *only* to dodge the self-invocation proxy trap (§7) |
| `AuthService` | mint dev tokens | dev-only login; deterministic auth id from email |

A few worth reading closely:

- **`OrderIngestionService.ingest(...)`** is the convergence point of both ingestion paths
  (push webhook + pull sync). It's **idempotent**: if an order with that `external_id` already
  exists for the merchant, it returns `false` and does nothing — so a webhook and a later
  reconciliation sync never double-count. It also decrements inventory and raises a `new_order`
  alert. Study how one well-designed method serves two callers.

- **`AnalyticsService`** shows when to leave JPA behind. Aggregations (`date_trunc`,
  `sum`, `group by`, period comparisons) are far cleaner as SQL than as entity code, so it uses
  `NamedParameterJdbcTemplate`. It still runs inside `@Transactional` so the tenant aspect sets
  the RLS variable on the same connection.

- **`WebhookService` + `WebhookPersistenceService`** are a deliberate pair. `WebhookService`
  verifies the HMAC signature and resolves the tenant (no transaction yet); then it calls the
  *separate* `WebhookPersistenceService` bean so the `@Transactional` proxy actually engages.
  This is the self-invocation lesson made concrete.

> **Concept — HMAC signature verification (webhooks):** the shop API signs the raw request
> body with a shared secret (`HMAC-SHA256`) and sends the hex digest in a header. We recompute
> it and compare with a constant-time check (`MessageDigest.isEqual`). If they differ, the
> request is forged → reject. This is how you trust an *unauthenticated* public endpoint.

---

## 11. Controllers

`web/` holds the **REST controllers** — the thin HTTP layer. They parse/validate input,
call a service, and return a DTO. No business logic.

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {
  private final ProductService products;
  public ProductController(ProductService products) { this.products = products; } // constructor injection

  @GetMapping
  public PageResponse<ProductResponse> list(@RequestParam(required=false) String q,
                                            @RequestParam(defaultValue="0") int page,
                                            @RequestParam(defaultValue="20") int size) {
    return products.list(q, page, Math.min(size, 100));
  }

  @PostMapping
  public ProductResponse create(@Valid @RequestBody ProductRequest req) { return products.create(req); }
}
```

Annotations to learn:
- `@RestController` — a controller whose return values are serialized straight to the response
  body (as JSON), not treated as view names.
- `@RequestMapping("/api/products")` — base path for the class.
- `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping` — HTTP verb + sub-path.
- `@RequestParam` — query/string parameters (`?q=...&page=...`), with defaults.
- `@PathVariable` — a path segment (`/products/{id}`).
- `@RequestBody` — deserialize the JSON request body into an object (Jackson).
- `@Valid` — run Bean Validation on the body before the method executes.
- **Constructor injection** — Spring passes the `ProductService` bean in. (Preferred over field
  injection: makes dependencies explicit and the class testable.)

The controllers map 1:1 to features: `AuthController`, `MeController`, `ProductController`,
`InventoryController`, `OrderController`, `AnalyticsController`, `AlertController`,
`SyncController`, `WebhookController`. `WebParams` is a small helper for lenient date parsing.

Note `WebhookController` takes `@RequestBody String rawBody` (not a parsed object) — it needs
the *exact* bytes to verify the HMAC signature before trusting/parsing them.

---

## 12. Error handling

### `web/error/ApiExceptions.java`

A set of typed exceptions carrying an HTTP status: `NotFound` (404), `BadRequest` (400),
`Conflict` (409), `Unauthorized` (401), `Forbidden` (403). Services throw these in plain
business language (`throw new ApiExceptions.NotFound("Product not found")`).

### `web/error/GlobalExceptionHandler.java`

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiExceptions.ApiException.class)
  public ResponseEntity<ApiError> handle(ApiExceptions.ApiException ex) { ... }
}
```

> **Concept — `@RestControllerAdvice`:** a global interceptor for exceptions thrown by *any*
> controller. Each `@ExceptionHandler` method turns one exception type into a clean JSON error
> response (`{ timestamp, status, message, errors }`) with the right status code. This keeps
> every controller free of try/catch — they just throw, and this class formats the response.
> It also handles validation failures (`MethodArgumentNotValidException` → 400 with field
> messages).

---

## 13. Integration & scheduling

### `integration/ShopApiClient.java`

A thin client over the external (mock) shop API using Spring's modern `RestClient`. Methods:
`fetchProducts(apiKey)` and `fetchOrders(apiKey, since)`. It sends the merchant's API key in a
header and maps the JSON responses into `ShopDtos` records.

> **Concept — `RestClient`:** Spring's fluent, synchronous HTTP client
> (`client.get().uri(...).retrieve().body(Type.class)`). The successor to `RestTemplate`.

### `scheduler/SchedulingConfig.java` + `SyncScheduler.java`

The **pull-sync** runs periodically as a reliability backstop for missed webhooks.

- `SyncScheduler.runAllTenants()` loops over every merchant, sets `TenantContext` for each,
  and calls `SyncService.doSync(...)` (a *separate* bean → proxy/transaction engage).
- `SchedulingConfig implements SchedulingConfigurer` registers that method as a fixed-delay
  task — **but only if `merchanthub.sync-interval-ms > 0`**, so setting it to `0` cleanly
  disables the scheduler. (We did it programmatically instead of `@Scheduled(fixedDelay=...)`
  precisely so `0` could mean "off" — a fixed annotation value can't express that.)

> **Concept — scheduled tasks:** `@EnableScheduling` (in `AppConfig`) plus a registered task
> runs background work on a timer, off the request threads. Because there's no HTTP request,
> the scheduler must set the tenant context itself for each merchant.

---

## 14. Tests

`src/test/java` contains **Testcontainers** integration tests.

> **Concept — Testcontainers:** spins up a real Postgres in a throwaway Docker container for
> the test, so you test against the actual database (RLS, real SQL) instead of mocks or an
> in-memory H2 that behaves differently.

- `AbstractIntegrationTest` — boots the full Spring context against a Testcontainers Postgres
  (`@SpringBootTest` + `@DynamicPropertySource` to point the datasource at the container).
- `TenantIsolationIntegrationTest` — proves merchant A can't see merchant B's products, and
  that a correctly-signed webhook ingests an order while a bad signature is rejected.

Run them with `cd backend && mvn test` (needs Docker). The Docker *image* build skips test
compilation (`-Dmaven.test.skip=true`) so the runtime image doesn't require a Docker-in-Docker
setup.

---

## 15. Glossary

Quick definitions of the recurring concepts, for reference:

- **Bean** — an object Spring creates and manages.
- **Dependency Injection (DI)** — Spring supplies a bean's collaborators (usually via the
  constructor) instead of the bean creating them.
- **`@Component` / `@Service` / `@Repository` / `@RestController` / `@Configuration`** —
  stereotypes that make a class a bean; they differ mainly in intent (and a couple add extra
  behavior, e.g. `@Repository` translates DB exceptions).
- **Proxy** — a wrapper Spring puts around a bean to add behavior (transactions, aspects).
  Only intercepts calls from *outside* the bean (hence the self-invocation trap).
- **AOP / Aspect** — cross-cutting code applied "around" many methods (e.g. transactions,
  tenant scoping).
- **`@Transactional`** — run a method in a DB transaction (commit on success, rollback on
  exception).
- **JPA / Hibernate** — map Java objects to database rows. JPA is the spec, Hibernate the impl.
- **Spring Data repository** — an interface Spring implements into queries at runtime.
- **DTO** — the data shape exposed at the API boundary (vs. internal entities).
- **JWT** — a signed token proving the caller's identity, sent per request.
- **RLS** — Postgres Row-Level Security; a per-table policy that filters rows automatically.
- **Flyway migration** — a versioned SQL file that evolves the schema.
- **ThreadLocal** — per-thread storage; how we carry "the current merchant" through a request.

---

## 16. Learning path

A sensible order to read the code with this guide open:

1. **Start at the edge:** `MerchantHubApplication` → `application.yml` → `pom.xml`. Understand
   how it boots and is configured.
2. **Follow one read request:** `ProductController.list` → `ProductService.list` →
   `ProductRepository.search`. See the layers and DTO mapping.
3. **Then security:** `JwtAuthFilter` → `JwtService` → `SecurityConfig`. Understand how a
   request becomes "authenticated as merchant X".
4. **Then the magic:** `TenantContext` → `TenantIsolationAspect` → `V2__rls_policies.sql`.
   Re-read §7 until the ordering + RLS click — this is the most valuable concept here.
5. **Then a write path:** `ProductController.create` → `ProductService.create` (uniqueness
   check, inventory upsert). Notice `@Transactional`.
6. **Then ingestion:** `WebhookController` → `WebhookService` → `WebhookPersistenceService` →
   `OrderIngestionService`. Learn HMAC verification, the self-invocation fix, and idempotency.
7. **Then analytics:** `AnalyticsService` — raw SQL aggregation and why it's not JPA.
8. **Then background work:** `SchedulingConfig` → `SyncScheduler` → `SyncService`.

### Exercises to cement it

- **Add a field:** add `barcode` to `Product`. You'll touch a new migration (`V4__...sql`),
  the entity, the DTOs, and the CSV import — a full vertical slice.
- **Add an endpoint:** `GET /api/analytics/revenue-by-product` returning revenue per product.
  Write the SQL in `AnalyticsService`, a DTO, and a controller method.
- **Add a rule:** reject creating a product with a negative price with a clear 400 (try it via
  validation *and* via a service-level check; compare).
- **Prove isolation yourself:** log in as `demo@` and `rival@`, hit `/api/products` with each
  token, and confirm the SKU lists differ. Then temporarily remove a `WHERE merchant_id` in a
  query and watch RLS still protect you.
- **Break and fix the proxy trap:** move `WebhookPersistenceService.persist` back *into*
  `WebhookService` as a private method and observe the tenant scoping / transaction silently
  not applying. Then revert. This teaches the proxy concept better than any paragraph.

---

Happy reading. If anything here is unclear, open the referenced file alongside the section —
the code is commented with the same reasoning.
