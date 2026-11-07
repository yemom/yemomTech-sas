# ADR-0001: Modular monolith over microservices

- **Status:** Accepted (2026-11)
- **Context:** YemomTech starts as a small team building one product with
  four bounded contexts that exist today (auth, organizations/memberships,
  invitations, websites) and several planned (SEO, leads, analytics). The
  operational cost of microservices (network boundaries, distributed
  transactions, deployment orchestration) is pure overhead before product
  fit is proven.
- **Decision:** One Spring Boot deployable, structured as **modules with
  enforced boundaries**: `com.bizgrow.<module>` owns its entities,
  repositories, services, and DTOs. Cross-module access goes through the
  target module's service — never its repository. Controllers stay thin;
  transactions live at the service boundary.
- **Consequences:**
  - ✅ Single test suite, single deploy, ACID transactions across
    auth+org+membership in one request.
  - ✅ Module boundaries (package visibility + review) preserve the option
    to extract a service later where it genuinely earns its keep.
  - ❌ A bug in one module can take down all of them (no isolation);
    scaling is vertical-only for now.
  - ❌ Requires discipline: a stray cross-module repository call compiles
    fine — boundaries are conventions enforced in review, not by the
    compiler (could add ArchUnit later).
