# hcen-fhir-interop-poc

Prueba de concepto de **interoperabilidad FHIR R4 sobre documentos CDA y metadatos XDS.b**, en Java con [HAPI FHIR](https://hapifhir.io/).

La idea es mostrar cómo se puede **agregar una capa FHIR sin tocar** lo que ya funciona con XDS.b y CDA: el documento CDA original se conserva tal cual, y la capa nueva publica `Patient` y `DocumentReference` que apuntan a él.

> **Alcance.** Es una PoC didáctica. No se conecta a ninguna plataforma real (ni HCEN ni otra), no maneja autenticación, guarda todo en memoria y usa datos 100 % ficticios. No es una implementación conforme de IHE MHD ni de ningún perfil.

## Qué hace

```
                    ┌──────────────────────────────┐
  CDA R2 (XML) ───► │ POST /bridge/cda             │
                    │  1. lee la cabecera CDA      │
                    │  2. deriva metadatos XDS.b   │
                    │  3. los mapea a FHIR R4      │
                    └──────────────┬───────────────┘
                                   │ (documento original intacto)
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          ▼                          ▼
 GET /fhir/Patient/{id}   GET /fhir/DocumentReference   GET /documents/{id}
                          ?patient={id}                 (el CDA original)
```

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/fhir/metadata` | `CapabilityStatement` R4 |
| GET | `/fhir/Patient/{id}` | Lectura de un paciente |
| GET | `/fhir/Patient?identifier=[sistema\|]valor` | Búsqueda por identificador |
| GET | `/fhir/DocumentReference/{id}` | Lectura de una referencia a documento |
| GET | `/fhir/DocumentReference?patient={id}` | Documentos de un paciente (`Bundle` searchset) |
| GET | `/documents/{id}` | El CDA original |
| POST | `/bridge/cda` | Ingesta de un CDA; responde `201` con el `DocumentReference` creado. Es idempotente |

Los errores se devuelven como `OperationOutcome` (400, 404, 405, 413, 500).

## Mapeo XDS.b → FHIR (`DocumentReference`)

El mapeo sigue la lógica de IHE MHD:

| XDS.b `DocumentEntry` | FHIR `DocumentReference` |
|---|---|
| `uniqueId` | `masterIdentifier` |
| `entryUUID` | `identifier` |
| `typeCode` | `type` |
| `classCode` | `category` |
| `formatCode` | `content.format` |
| `mimeType`, `size`, `hash` (SHA-1), `URI` | `content.attachment.contentType`, `size`, `hash`, `url` |
| `title`, `languageCode` | `content.attachment.title`, `language` |
| `creationTime` | `date` |
| `patientId` | `subject` |
| `confidentialityCode` | `securityLabel` |
| `authorPerson` | `author` |
| institución custodia | `custodian` |

En la PoC los metadatos XDS.b se **derivan de la cabecera CDA**. En un despliegue real vendrían del registro XDS. La cabecera CDA se mapea a `Patient` (identificador, nombre, sexo, fecha de nacimiento).

## Ejecutar

Requisitos: Java 17+ y Maven 3.9+.

```bash
mvn verify                       # compila y corre los tests
java -jar target/hcen-fhir-interop-poc-0.1.0.jar --load-sample
```

Con `--load-sample` (o `LOAD_SAMPLE=true`) se carga un CDA ficticio de ejemplo. Variables de entorno: `PORT` (8080) y `PUBLIC_BASE_URL` (para los enlaces absolutos).

```bash
curl http://localhost:8080/fhir/metadata
curl "http://localhost:8080/fhir/Patient?identifier=EJ-0001"
curl "http://localhost:8080/fhir/DocumentReference?patient=<id-del-paciente>"

# Ingesta de otro CDA
curl -X POST -H "Content-Type: application/xml" \
     --data-binary @src/main/resources/samples/informe-alta-ficticio.xml \
     http://localhost:8080/bridge/cda
```

## CI/CD

| Etapa | Dónde | Qué hace |
|---|---|---|
| **CI** | `.github/workflows/ci.yml` | En cada push: compila, corre los tests y construye la imagen Docker |
| **CD (GitHub Actions)** | `.github/workflows/cd-openshift.yml` | Manual o al publicar una etiqueta `v*`: tests, publica la imagen en GitHub Container Registry, análisis con Trivy (informativo), la importa al ImageStream de OpenShift y espera el rollout |
| **CI/CD nativo en OpenShift (Tekton)** | `tekton/` | `Pipeline` de 4 tareas: clonar, `mvn verify`, `oc start-build` y esperar el rolling update |
| **Recursos de OpenShift** | `openshift/` | `ImageStream`, `BuildConfig` (build Docker desde Git), `Deployment` con trigger de imagen y rolling update, `Service`, `Route`, `HPA` |

### Contenedor local

```bash
docker build -t hcen-fhir-interop-poc .
docker run -p 8080:8080 -e LOAD_SAMPLE=true hcen-fhir-interop-poc
```

### Desplegar en OpenShift

> Los manifiestos y los pipelines **no fueron probados contra un clúster** al escribirlos. Hay que reemplazar `REEMPLAZAR-USUARIO` (URL del repo) y `REEMPLAZAR-HOST-DE-LA-ROUTE`.

```bash
oc apply -k openshift/                     # ImageStream, BuildConfig, Deployment, Service, Route, HPA
oc start-build hcen-fhir-interop-poc --follow
oc get route hcen-fhir-interop-poc         # copiar el host a PUBLIC_BASE_URL del Deployment
```

El `Deployment` lleva la anotación `image.openshift.io/triggers`: cada vez que el build actualiza el ImageStream, OpenShift hace un **rolling update** (`maxUnavailable: 0`). Para volver atrás:

```bash
oc rollout history deployment/hcen-fhir-interop-poc
oc rollout undo deployment/hcen-fhir-interop-poc
```

El `HPA` escala de 1 a 3 réplicas con 70 % de CPU (el `Deployment` define `requests` de CPU, necesarios para que funcione).

### Pipeline de Tekton

Requiere OpenShift Pipelines instalado en el clúster.

```bash
oc apply -f tekton/tasks.yaml -f tekton/pipeline.yaml
oc create -f tekton/pipelinerun.yaml       # usa generateName: create, no apply
tkn pipelinerun logs --last -f             # opcional, si se tiene la CLI tkn
```

Las `Task` son propias (no dependen de `ClusterTask` ni del Tekton Hub) y usan la API `tekton.dev/v1`; si el clúster solo ofrece `v1beta1`, basta con cambiar el `apiVersion`.

Limitación conocida: el pipeline prueba la revisión indicada en `git-revision`, pero el build lo hace el `BuildConfig`, que compila la rama configurada en `source.git.ref` (por defecto `main`).

### CD con GitHub Actions

Secretos necesarios en el repositorio: `OPENSHIFT_SERVER`, `OPENSHIFT_TOKEN`, `OPENSHIFT_NAMESPACE`. La imagen en `ghcr.io` debe ser pública (o el proyecto de OpenShift debe tener un secreto de pull). El análisis con Trivy es informativo: con `exit-code: "1"` pasa a bloquear el despliegue.

## Decisiones técnicas

- **HAPI FHIR solo para el modelo y la serialización** (`hapi-fhir-structures-r4`). La fachada HTTP usa el servidor del JDK para mantener la PoC pequeña y fácil de leer.
- **Parser CDA sin dependencias** (DOM del JDK) con DOCTYPE y entidades externas deshabilitados (protección contra XXE) y tope de 2 MB en la ingesta.
- **Ids determinísticos** (UUID por nombre a partir de los identificadores del documento y del paciente): reenviar el mismo CDA no duplica recursos.
- Las marcas de tiempo HL7 sin zona horaria se interpretan como UTC.

## Limitaciones y siguientes pasos

- Pasar a `hapi-fhir-server` (`RestfulServer` con *resource providers*) para obtener paginación, `_include`, validación y más parámetros de búsqueda.
- Validar los recursos contra perfiles (por ejemplo, los de MHD) con el validador de HAPI.
- Webhook de GitHub para disparar el pipeline en cada push (`EventListener` y `TriggerBinding` de Tekton).
- Agregar las transacciones MHD (ITI-65 / ITI-67) en lugar de un `POST` propio.
- Persistencia real en lugar del almacén en memoria.
- Autenticación y autorización (OAuth 2.0 / SMART on FHIR) y auditoría.
- Mapear también el cuerpo del CDA (secciones y entradas) a recursos clínicos, no solo la cabecera.

## Licencia

MIT. Ver [LICENSE](LICENSE).
