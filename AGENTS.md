# AGENTS.md

Spring Boot (Java 25) demo app for **OpenTelemetry / Micrometer tracing** with multiple backends
(Jaeger, Zipkin, Elastic APM, Prometheus). Single Maven module, groupId `ch.dboeckli.opentelemetry`,
package `ch.dboeckli.example.otel`. App on port `8080`.

## Build & test commands

- Full build: `./mvnw clean verify` — spring-javaformat + Spotless format checks, unit (`*Test`) and
  integration (`*IT`) tests, Helm lint/template.
- Unit tests only: `./mvnw test`. Single test: `./mvnw test -Dtest=HelloControllerTest#methodName`.
- `./mvnw clean install` additionally builds the Docker image and packages the Helm chart into
  `target/helm/repo/`. Skip the Docker build with `-Dskip.docker.build=true`.
- Start locally: `./mvnw spring-boot:run` (app on `:8080`).

After changing code, always verify: run the relevant Maven goal above and report its output
(evidence, not just "done").

## Formatting is enforced (fails the `validate` phase)

- Java: Spring Java Format → fix with `./mvnw spring-javaformat:apply`.
- Everything else (pom.xml, `**/*.md`, json, `src/main/resources/application*.yaml`, `**/*.sh`):
  Spotless → fix with `./mvnw spotless:apply`.
- Spotless flexmark also formats markdown, so this file and any `.md` edits must stay flexmark-clean;
  run `./mvnw spotless:apply` after editing markdown.

## Sandbox build quirk (background)

This sandbox mounts the repo via filesystem passthrough, which blocks symlinks — Spotless's
`npm install` (prettier) would fail with `EPERM` unless npm skips bin links. The sandbox kit sets
`npm_config_bin_links=false` globally (`spec.yaml` → `environment.variables`), so no manual export
is needed here. On a normal host (Windows/CI) this does not apply either.

## Observability stack

`compose.yaml` starts the backends (OTel Collector, Jaeger, Zipkin, Prometheus, Elasticsearch,
Kibana, APM Server); collector/Prometheus config lives in `monitoring/`. Architecture overview in
the README.

## Deployment

- Helm-only: chart in `helm-charts/`, packaged to `target/helm/repo/`, release name = artifactId,
  namespace `spring-with-micrometer-tracing`, NodePort `30080`.
- CI (`.github/workflows/`): `maven-build.yml` builds + deploys snapshots and triggers
  `deploy-and-test-cluster.yml`; `release.yml` runs the Maven release.
- Dependency updates are managed via `.github/dependabot.yml` and `.github/renovate.json`; validate
  changes with `renovate-config-validator`.
