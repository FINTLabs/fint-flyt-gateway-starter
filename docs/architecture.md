# Architecture

flyt-gateway-starter runs inside each gateway, rather than as a separate service. The context diagram shows the
embedding gateway; the component diagram shows the library inside it. Blue is the gateway or starter, turquoise is
external code and services. There is no separate container diagram because the starter has no deployment of its own.

## C4 level 1: System context

```mermaid
C4Context
    title A gateway using flyt-gateway-starter

    System_Ext(source, "Source application", "Sends instances and metadata.")
    System_Ext(kafka, "Kafka", "Instance, error and metadata events.")
    System(gateway, "FLYT gateway", "Maps and processes incoming payloads.")
    System_Ext(file, "file-service", "Stores attachments.")
    System_Ext(integration, "intgr-conf-service", "Integration status.")
    System_Ext(history, "history-service", "Archive instance IDs.")

    Rel(source, gateway, "HTTP")
    Rel(gateway, kafka, "Publishes")
    Rel(gateway, file, "Uploads")
    Rel(gateway, integration, "Looks up")
    Rel(gateway, history, "Looks up")

    UpdateRelStyle(source, gateway, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(gateway, kafka, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(gateway, file, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(gateway, integration, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(gateway, history, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateElementStyle(source, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(kafka, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(gateway, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(file, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(integration, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(history, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

flyt-security-starter validates source application tokens against NAM and the configured client ID. The gateway uses
NAM client credentials for internal HTTP calls. Source application authorization requires no authorization-service
lookup. `HistoryClient` is available to gateway code; normal instance processing does not call it.

## C4 level 3: Components

```mermaid
C4Component
    title Processing inside a FLYT gateway

    Component_Ext(controller, "Controllers", "Gateway code", "Incoming HTTP payloads.")
    Component(validation, "Validation", "Jakarta Validation", "Payload checks and error mapping.")
    Component(processors, "Processors", "Kotlin", "Instances, multipart and metadata.")
    Component(files, "File client", "RestClient", "Upload attachments with retry.")
    Component(producers, "Event producers", "KafkaTemplate", "Instance flow headers, JSON.")
    Component_Ext(services, "Internal HTTP APIs", "FLYT services", "Integration status and file storage.")
    Component_Ext(kafka, "Kafka", "Event topics", "Instances, errors and metadata.")

    Rel(controller, processors, "Delegates")
    Rel(processors, validation, "Validates")
    Rel(processors, files, "Persists")
    Rel(processors, producers, "Publishes")
    Rel(processors, services, "Integration")
    Rel(files, services, "Uploads")
    Rel(producers, kafka, "Sends")

    UpdateRelStyle(controller, processors, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(processors, validation, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(processors, files, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(processors, producers, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(processors, services, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(files, services, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(producers, kafka, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateElementStyle(controller, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(validation, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(processors, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(files, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(producers, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(services, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(kafka, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

- **Processors:** `InstanceProcessorFactoryService` creates JSON/Base64 or multipart processors with the gateway's
  mapper. They check integration status with `IntgrConfClient`, validate and map payloads, upload files and publish
  events. `IntegrationMetadataProcessor` validates and maps metadata, then publishes it. The no-ID overloads read
  `novari.flyt.security.external.source-application-id`; instance processors also accept an explicit ID.
- **HTTP clients:** `IntgrConfClient` and `HistoryClient` return `null` on 404. `FileClient` retries uploads up to five
  times with Spring Framework's `RetryTemplate`. `RestClientConfiguration` provides OAuth2 client credentials and
  timeouts, with separate registrations per target service.
- **Kafka:** producers use Boot's `KafkaTemplate`, topic names from `KafkaTopics` and the flyt-kafka header extensions.
  `KafkaConfiguration` declares the instance-error `NewTopic`. The starter registers no Kafka listeners.
- **HTTP limits and errors:** `RequestSizeLimitFilter` rejects oversized requests. Multipart and Jackson configuration
  set upload and string limits. `GlobalExceptionHandler` maps errors and preserves Spring MVC statuses such as 404/405.

## Auto-configuration

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` registers the configuration classes.
`GatewayAutoConfiguration` creates the services, request size filter and exception handler with
`@ConditionalOnMissingBean`, so applications can supply replacements. Other configuration classes provide HTTP clients,
Kafka topics, Jackson limits and multipart defaults.

Services and the filter have no component stereotypes. The exception handler retains `@RestControllerAdvice` for MVC;
if an existing component scan finds it, auto-configuration backs off. Applications need no scan of the starter, and
existing broad scans remain supported. Remove a broad scan only after checking whether other dependencies still need it.

Tests cover startup without scanning through `TestApplication`, Kafka publication with an embedded broker, advice
registration with legacy scanning, and application-supplied replacements.
