# FINT Flyt Gateway Starter

Spring Boot starter library for building FINT Flyt gateways. It validates and maps incoming instance payloads, checks integration status, uploads attached files to the file service, and publishes instance and error events to Kafka with consistent headers and error handling.

See [docs/architecture.md](docs/architecture.md) for C4 diagrams and auto-configuration.

## Features
- Validates incoming payloads with Jakarta Bean Validation and emits mapped error events.
- Looks up integrations in intgr-conf-service over HTTP and enforces deactivated/unknown integration rules.
- `HistoryClient` looks up the archive instance ID of an instance in history-service over HTTP.
- Uploads files with OAuth2 client credentials and retry logic.
- Publishes instance-received and instance-error events to Kafka.
- Publishes integration metadata to Kafka for gateway integrations that expose form or schema metadata.
- Provides autoconfiguration for Kafka topic setup and the RestClients for file-service, intgr-conf-service and history-service.

## Installation
Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("no.novari:flyt-gateway-starter:<version>")
}
```

## Usage
The starter registers its services through Spring Boot auto-configuration; no component scan is needed.
Create a processor using the factory and delegate your controller to it. The examples use
`novari.flyt.security.external.source-application`; no `Authentication` parameter is needed.

### Endpoint security

The controller examples use `/api/acos/**`. Configure the matching source application chain:

```yaml
novari:
  flyt:
    security:
      external:
        paths: /api/acos/**
        source-application: ACOS
```

Replace the path and `SourceApplication` with your gateway's values. The expected client ID is read from
`fint.flyt.<app>.sso.client-id`, loaded from the source application's NAM secret as shown under
[Upgrading from 5.x](#upgrading-from-5x).
`flyt-security-starter` validates the JWT and checks that the caller is the configured client before invoking the
controller. These external endpoints need no additional `@PreAuthorize`; routes outside the configured chains are
denied. Omitting `Authentication` from the controller does not make the endpoint public.

For internal user endpoints under `/api/intern/**`, use `@PreAuthorize` when an operation requires a higher role or
access to a particular source application. See the [security starter README](https://github.com/FINTLabs/fint-flyt-security-starter#access-to-source-applications).

### Instance controller

```kotlin
@RestController
class InstanceController(
    processorFactory: InstanceProcessorFactoryService,
    mapper: InstanceMapper<IncomingInstance>,
) {
    private val processor =
        processorFactory.createInstanceProcessor(
            sourceApplicationIntegrationIdFunction = { it.integrationId },
            sourceApplicationInstanceIdFunction = { it.instanceId },
            instanceMapper = mapper,
        )

    @PostMapping("/api/acos/instances")
    fun receive(
        @RequestBody instance: IncomingInstance,
    ): ResponseEntity<Void> = processor.processInstance(instance)
}
```

Implement `InstanceMapper` to map your inbound model to an `InstanceObject`, and use the provided `persistFile` callback for attachments.

```kotlin
@Component
class IncomingInstanceMapper : InstanceMapper<IncomingInstance> {
    override fun map(
        sourceApplicationId: Long,
        incomingInstance: IncomingInstance,
        persistFile: (File) -> UUID,
    ): InstanceObject {
        val fileId =
            persistFile(
                File(
                    name = incomingInstance.fileName,
                    sourceApplicationId = sourceApplicationId,
                    sourceApplicationInstanceId = incomingInstance.instanceId,
                    type = MediaType.APPLICATION_PDF,
                    encoding = "base64",
                    base64Contents = incomingInstance.fileBase64,
                ),
            )

        return InstanceObject(
            valuePerKey =
                mapOf(
                    "instanceId" to incomingInstance.instanceId,
                    "fileId" to fileId.toString(),
                ),
        )
    }
}
```

If your integration id is fixed, you can use the overload that accepts a constant:

```kotlin
processorFactory.createInstanceProcessor(
    sourceApplicationIntegrationId = "integration-id",
    sourceApplicationInstanceIdFunction = { it.instanceId },
    instanceMapper = mapper,
)
```

## Multipart usage
Existing JSON/Base64 integrations can keep using `InstanceProcessor` and `InstanceMapper`.
New integrations can use `MultipartInstanceProcessor` and send metadata as JSON in one part and binary files in separate parts.

```kotlin
@RestController
class MultipartInstanceController(
    processorFactory: InstanceProcessorFactoryService,
    mapper: MultipartInstanceMapper<IncomingInstance>,
) {
    private val processor =
        processorFactory.createMultipartInstanceProcessor(
            sourceApplicationIntegrationIdFunction = { it.integrationId },
            sourceApplicationInstanceIdFunction = { it.instanceId },
            multipartInstanceMapper = mapper,
        )

    @PostMapping("/api/acos/instances", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun receive(
        @RequestPart("instance") instance: IncomingInstance,
        @RequestPart("files") files: List<MultipartFile>,
    ): ResponseEntity<Void> = processor.processInstance(instance, files)
}
```

The multipart mapper links document metadata to the correct file part by returning a `MultipartFileReference`.
Use `originalFilename` when multiple uploaded files share the same part name.

```kotlin
@Component
class IncomingMultipartInstanceMapper : MultipartInstanceMapper<IncomingInstance> {
    override fun map(
        sourceApplicationId: Long,
        incomingInstance: IncomingInstance,
        persistFile: (MultipartFileReference) -> UUID,
    ): InstanceObject {
        val fileId =
            persistFile(
                MultipartFileReference(
                    partName = incomingInstance.filePartName,
                    fileName = incomingInstance.fileName,
                    originalFilename = incomingInstance.originalFilename,
                    type = MediaType.APPLICATION_PDF,
                ),
            )

        return InstanceObject(
            valuePerKey =
                mapOf(
                    "instanceId" to incomingInstance.instanceId,
                    "fileId" to fileId.toString(),
                ),
        )
    }
}
```

## Metadata usage
Gateways that expose integration metadata can use `IntegrationMetadataProcessor`.
The processor validates the incoming metadata, reads the source application ID from `novari.flyt.security.external.source-application`, maps it to `IntegrationMetadata`, and publishes an `integration-metadata-received` event.

```kotlin
@RestController
class MetadataController(
    private val processor: IntegrationMetadataProcessor,
    private val mapper: IntegrationMetadataMapper<IncomingMetadata>,
    private val validator: IntegrationMetadataValidator<IncomingMetadata>,
) {
    @PostMapping("/api/acos/metadata")
    fun receive(
        @RequestBody metadata: IncomingMetadata,
    ): ResponseEntity<Void> =
        processor.processIntegrationMetadata(
            incomingMetadata = metadata,
            integrationMetadataMapper = mapper,
            integrationMetadataValidator = validator,
        )
}
```

Implement `IntegrationMetadataMapper` to describe each integration and its supported instance metadata.
Return validation errors from `IntegrationMetadataValidator` to reject invalid metadata with `422 Unprocessable Entity`.

```kotlin
@Component
class IncomingMetadataMapper : IntegrationMetadataMapper<IncomingMetadata> {
    override fun toIntegrationMetadata(
        sourceApplicationId: Long,
        incomingMetadata: IncomingMetadata,
    ): IntegrationMetadata =
        IntegrationMetadata(
            sourceApplicationId = sourceApplicationId,
            sourceApplicationIntegrationId = incomingMetadata.integrationId,
            sourceApplicationIntegrationUri = incomingMetadata.integrationUri,
            integrationDisplayName = incomingMetadata.displayName,
            version = incomingMetadata.version,
            instanceMetadata = InstanceMetadataContent(
                instanceValueMetadata = incomingMetadata.fields,
            ),
        )
}
```

## Configuration
Required:
- `novari.flyt.file-service-url` - base URL for the file service.

Optional:
- `novari.flyt.gateway-starter.check-integration-exists` (default `true`)
- `novari.flyt.gateway-starter.max-request-size` (default `100MB`)
- `novari.flyt.gateway-starter.jackson.max-string-length` (default: same as `max-request-size`; override with values like `120MB` or bytes)
- `spring.servlet.multipart.max-file-size` (default: `novari.flyt.gateway-starter.max-request-size` unless explicitly set)
- `spring.servlet.multipart.max-request-size` (default: `novari.flyt.gateway-starter.max-request-size` unless explicitly set)
- `novari.flyt.gateway-starter.kafka.topic.instance-error.retention-time` (default `PT96H`)
- `novari.flyt.gateway-starter.kafka.topic.instance-error.cleanup-frequency` (default `NORMAL`)
- `novari.flyt.gateway-starter.kafka.topic.instance-error.partitions` (default `1`)

- `novari.flyt.intgr-conf-service-url` (default `http://fint-flyt-intgr-conf-service:8080${server.servlet.context-path:}`)
- `novari.flyt.history-service-url` (default `http://fint-flyt-history-service:8080${server.servlet.context-path:}`)
- `spring.http.clients.connect-timeout` (default `2s`) and `spring.http.clients.read-timeout` (default `5s`) for intgr-conf-service, history-service and the token request to NAM. The defaults apply only to these. file-service always uses 300 s.

The URL defaults assume the app runs with the same context path as the two services, which is the case in all FLYT overlays.

### OAuth clients

The gateway needs two OAuth clients (`NamOAuthClientApplicationResource`, `client_credentials`) and three registrations:

| Registration id      | OAuth client                            | Used for                                     |
| -------------------- | --------------------------------------- | -------------------------------------------- |
| `file-service`       | `fint-flyt-file-oauth2-client`          | Uploading files                              |
| `intgr-conf-service` | `fint-flyt-authorization-oauth2-client` | `GET /api/intern-klient/integrasjoner`       |
| `history-service`    | `fint-flyt-authorization-oauth2-client` | `GET /api/intern-klient/archive-instance-id` |

The registration ids are fixed. A 404 from intgr-conf-service or history-service is returned as `null`. Other errors are
thrown. Add an `authorization-service` registration only if the gateway uses
[`FlytAccess`](https://github.com/FINTLabs/fint-flyt-security-starter#access-to-source-applications).

`fint-flyt-authorization-oauth2-client` is the shared OAuth client for internal calls between FLYT services, because
namerator does not create new NAM clients. The registrations stay separate per target service, so each can get its own
client later by changing configuration only.

Every NAM secret has the keys `fint.sso.client-id` and `fint.sso.client-secret`. Load the file-service client with
`envFrom` and map the shared client to its own property names with `secretKeyRef`, so the values do not collide:

```yaml
envFrom:
  - secretRef:
      name: fint-flyt-file-oauth2-client
env:
  - name: fint.flyt.authorization.sso.client-id
    valueFrom:
      secretKeyRef:
        name: fint-flyt-authorization-oauth2-client
        key: fint.sso.client-id
  - name: fint.flyt.authorization.sso.client-secret
    valueFrom:
      secretKeyRef:
        name: fint-flyt-authorization-oauth2-client
        key: fint.sso.client-secret
```

In `ra-no` the shared client comes from 1Password with the keys `fint.flyt.authorization.sso.client-id` and
`fint.flyt.authorization.sso.client-secret`, so the overlay must patch `secretKeyRef.key`.

Do not use `clientIdProperty` on the NAM resource. The services declare the same resource and read `fint.sso.client-id`
from it.

```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          fint-idp:
            token-uri: https://idp.felleskomponent.no/nidp/oauth/nam/token?resourceServer=fint-api
        registration:
          file-service:
            authorization-grant-type: client_credentials
            provider: fint-idp
            client-id: ${fint.sso.client-id}
            client-secret: ${fint.sso.client-secret}
          intgr-conf-service:
            authorization-grant-type: client_credentials
            provider: fint-idp
            client-id: ${fint.flyt.authorization.sso.client-id}
            client-secret: ${fint.flyt.authorization.sso.client-secret}
          history-service:
            authorization-grant-type: client_credentials
            provider: fint-idp
            client-id: ${fint.flyt.authorization.sso.client-id}
            client-secret: ${fint.flyt.authorization.sso.client-secret}
```

### Upgrading from 5.x

- Requires Spring Boot 4.2+, Jackson 3 and Java 21+. flyt-kafka 8 replaces 7, and flyt-security-starter replaces
  flyt-web-resource-server 5. Follow the [Kafka migration guide](https://github.com/FINTLabs/fint-kafka-starter/blob/main/docs/migrating-from-fint-kafka.md)
  and [security migration guide](https://github.com/FINTLabs/fint-flyt-security-starter#migrating-from-fint-flyt-web-resource-server-5).
- Set `novari.flyt.security.external.paths` to explicit gateway routes (e.g. `/api/acos/**`, not `/api/**`) and
  `novari.flyt.security.external.source-application` to the gateway's `SourceApplication` (e.g. `ACOS`). Load the
  source application's client ID from the NAM secret authorization-service owns:

  ```yaml
  # Under spec.env in the gateway's Flais configuration
  - name: fint.flyt.acos.sso.client-id
    valueFrom:
      secretKeyRef:
        name: fint-flyt-acos-oauth2-client
        key: fint.flyt.acos.sso.client-id
  ```

- `processInstance(authentication, ...)` and `processIntegrationMetadata(authentication = ...)` are removed. Use the
  examples above. The `processInstance(sourceApplicationId, ...)` overload remains for callers such as Kafka consumers.
- The starter now auto-configures its services. Remove `scanBasePackages = ["no.novari"]` if no other dependency
  needs it. Existing scanning remains supported without duplicate starter beans.
- Rename `spring.http.client.*` to `spring.http.clients.*` and `novari.kafka.default-replicas` to
  `novari.kafka.topic.default-replicas`. Remove `kafkaConnectivity` and `kafkaConsumers` from health group includes.
- The starter no longer needs `spring-retry` or `aspectjweaver`. Remove them if the app has no other use for them.
- Unknown routes and unsupported HTTP methods now return 404 and 405 instead of 500.

### Upgrading from 4.x

- `IntegrationRequestProducerService` and `ArchiveCaseIdRequestService` are removed. Use `IntgrConfClient` and `HistoryClient.getArchiveInstanceId(sourceApplicationId, sourceApplicationInstanceId)`.
- Add the OAuth clients and registrations above. When upgrading to 6.x, also follow the 5.x migration steps above.
- The beans `fileAuthorizedClientManager` and `fileClientHttpRequestFactory` are replaced by `internalClientAuthorizedClientManager`.
- The gateway no longer uses Kafka request/reply, so `novari.kafka.reply-topic.*` has no effect.

## Dependency Locking

Versions and plugins are declared in `gradle/libs.versions.toml`. Dependencies are locked in `gradle.lockfile` and
`settings-gradle.lockfile`; plugin versions are pinned in the catalog. Dependabot updates them in its own PRs. After a
manual change that affects dependency resolution, run:

```bash
./gradlew dependencies --write-locks
```

Commit the updated lockfiles with the change. The Spring Boot BOM is used only inside the build; the published POM
contains resolved versions and no BOM.

## Binary compatibility

ABI validation is not enabled: gateways compile against the starter version they use, and no other libraries compile
against it. There is no known dependency diamond that could replace that version at runtime. An API dump and
`explicitApi()` would add maintenance without a current need. Reconsider if another library starts depending on the starter.
