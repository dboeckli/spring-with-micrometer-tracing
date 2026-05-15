# Spring Boot 6 Open Telemetry Project

## Observability / Monitoring Setup

Dieses Projekt bringt ein vollständiges Observability-Setup mit **OpenTelemetry** und mehreren Backends mit.
Siehe auch: https://last9.io/blog/opentelemetry-for-spring/

### Architekturüberblick

#### Traces

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                       Anwendung (spring-with-otel) with opentelemetry-spring-boot-starter │
└──────────────────────────────────────────────┬────────────────────────────────────────────┘
                                               │  Push OTLP (Traces)
                                               v
       ┌─────────────────────────────────────────────────────────────────────────────────┐
       │                           OTEL COLLECTOR                                        │
       │                           (otel-collector)                                      │
       └───────────────┬──────────────────────────────┬────────────────────────┬─────────┘
           Push Traces │                              │                        │
                       │                              │                        │
                       v                              v                        v
         ┌─────────────────────────┐     ┌─────────────────────────┐   ┌──────────────────────────┐
         │         JAEGER          │     │         ZIPKIN          │   │     ELASTIC APM SERVER   │
         │       jaeger:4317       │     │       zipkin:9411       │   │      apm-server:8200     │
         └─────────────────────────┘     └─────────────────────────┘   └───────────────┬──────────┘
                                                                                       │
                                                                                       │ Push Traces
                                                                                       v
                                                                           ┌──────────────────────────┐
                                                                           │       ELASTICSEARCH      │
                                                                           │      localhost:9200      │
                                                                           └───────────────┬──────────┘
                                                                                           │
                                                                                           v
                                                                                   ┌───────────────────┐
                                                                                   │      KIBANA       │
                                                                                   │   localhost:5601  │
                                                                                   └───────────────────┘

Hinweise:
- Traces gehen an **Jaeger**, **Zipkin** und **Elastic APM**
- Elastic APM schreibt Traces **in Elasticsearch**
- Kibana visualisiert (sofern aktiviert) Traces aus Elasticsearch


```

#### Metrics

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                       Anwendung (spring-with-otel) with opentelemetry-spring-boot-starter │
└────────────────┬──────────────────────────────────────────────────────────────────────────┘
                 │  Push OTLP (Metrics)
                 v
       ┌─────────────────────────────────────────────────────────────────────┐
       │                          OTEL COLLECTOR                             │
       │                          (otel-collector)                           │
       │             Metrics Exporter: otel-collector:8889                   │
       └───────────────────────────────────────────────┬─────────────────────┘
   Pull OTLP (Metrics) ^                               │  Push OTLP (Metrics)
                       │                               │
                       │                               v
         ┌──────────────────────────┐        ┌──────────────────────────┐
         │       PROMETHEUS         │        │     ELASTIC APM SERVER   │
         │     localhost:9090       │        │      apm-server:8200     │
         │   scrapt 8889 (Collector)│        └───────────────┬──────────┘
         └──────────────────────────┘                        │
                                                             │ Push Metrics
                                                             v
                                                ┌──────────────────────────┐
                                                │      ELASTICSEARCH       │
                                                │     localhost:9200       │
                                                └───────────────┬──────────┘
                                                                │
                                                                v
                                                        ┌───────────────────┐
                                                        │      KIBANA       │
                                                        │   localhost:5601  │
                                                        └───────────────────┘

Hinweise:
- Metriken gehen an Prometheus **und** an Elastic APM
- Elastic APM schreibt nach Elasticsearch.
- Prometheus scrapt (pulled) diese Daten von dem Collector.
- Kibana visualisiert Daten aus Elasticsearch.
```

#### Logs

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                  Anwendung (spring-with-otel-starter) opentelemetry-spring-boot-starter   │
└───────────────┬───────────────────────────────────────────────────────────────────────────┘
                │  Push OTLP (Logs)
                v
      ┌─────────────────────────────────────────────────────┐
      │                   OTEL COLLECTOR                    │
      │                   (otel-collector)                  │
      └──────────────────────────────┬──────────────────────┘
                                     │  Push OTLP (Logs)
                                     v
                        ┌──────────────────────────┐
                        │      ELASTICSEARCH       │
                        │     localhost:9200       │
                        └───────────────┬──────────┘
                                        │
                                        v
                                ┌───────────────────┐
                                │      KIBANA       │
                                │   localhost:5601  │
                                └───────────────────┘


```

**App → Otel Collector**
Die Spring Boot Anwendung exportiert **Traces, Metrics und Logs** per **OTLP HTTP** an den Otel Collector:

- OTLP HTTP Endpoint der App: `http://localhost:4318` (aus Sicht des Hosts).
- Per Port-Mapping geht das an den Collector-Container (`otel-collector:4318`).
- **Otel Collector → Backends**

  Der Collector verteilt die Telemetriedaten wie folgt:

  - **Traces**
    - → Jaeger (`jaeger:4317`)
    - → Zipkin (`zipkin:9411`)
    - → Elastic APM Server (`apm-server:8200`, OTLP HTTP)
  - **Metrics**
    - → Prometheus-Exporter (`otel-collector:8889`)  
      Prometheus scrapt diesen Endpoint.
    - → Elastic APM Server (`apm-server:8200`, OTLP HTTP)
  - **Logs**
    - → Elasticsearch (`elasticsearch:9200`), Darstellung über Kibana.
- **Prometheus → Otel Collector**

  Prometheus ist ausschließlich mit dem **Collector** verbunden:

  - scrape target: `otel-collector:8889`
  - die Anwendung selbst wird **nicht** direkt über `/actuator/prometheus` gescrapt.

### Dienste und UIs

folgende Ui's stehen zur Verfügung:

- **Prometheus** – Metriken
  - URL: `http://localhost:9090`
  - Unter `Status → Targets` sollte `otel-collector` als „UP“ erscheinen.
- **Jaeger** – Traces
  - URL: `http://localhost:16686`
  - Suche nach Services wie `spring-with-otel`.
- **Zipkin** – Traces (Alternative UI)
  - URL: `http://localhost:9411`
- **Elasticsearch + Kibana** – Logs und (abhängig von APM-Konfiguration) Metriken/Traces
  - Elasticsearch: `http://localhost:9200`
  - Kibana: `http://localhost:5601`:
    Go to: Stack Management -> Data views -> APM
    search for Index pattern:
    traces-apm*,apm-*,traces-*.otel-*,logs-apm*,apm-*,logs-*.otel-*,metrics-apm*,apm-*,metrics-*.otel-*
- **Elastic APM Server** – OTLP-Endpunkt für APM
  - OTLP HTTP: `http://localhost:8200` (per Port-Mapping auf `apm-server:8200`)

