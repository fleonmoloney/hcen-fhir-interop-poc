package uy.fhirpoc.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import ca.uhn.fhir.context.FhirContext;
import uy.fhirpoc.bridge.CdaBridge;
import uy.fhirpoc.cda.CdaParseException;
import uy.fhirpoc.store.InMemoryStore;

/**
 * Fachada FHIR R4 mínima, sobre el servidor HTTP del JDK. Usa HAPI FHIR para el modelo
 * y la serialización JSON.
 *
 * <ul>
 *   <li>GET  /fhir/metadata</li>
 *   <li>GET  /fhir/Patient/{id} y /fhir/Patient?identifier=[sistema|]valor</li>
 *   <li>GET  /fhir/DocumentReference/{id} y /fhir/DocumentReference?patient={id}</li>
 *   <li>GET  /documents/{id}  (el CDA original)</li>
 *   <li>POST /bridge/cda      (ingesta de un CDA; crea Patient + DocumentReference)</li>
 * </ul>
 */
public final class FhirFacadeServer implements AutoCloseable {

    private static final FhirContext CTX = FhirContext.forR4Cached();
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final String FHIR_JSON = "application/fhir+json; charset=utf-8";

    private static final String CAPABILITY_STATEMENT = """
            {
              "resourceType": "CapabilityStatement",
              "status": "active",
              "date": "2026-11-17",
              "kind": "instance",
              "software": { "name": "hcen-fhir-interop-poc", "version": "0.1.0" },
              "fhirVersion": "4.0.1",
              "format": [ "json" ],
              "rest": [ {
                "mode": "server",
                "resource": [
                  { "type": "Patient",
                    "interaction": [ { "code": "read" }, { "code": "search-type" } ],
                    "searchParam": [ { "name": "identifier", "type": "token" } ] },
                  { "type": "DocumentReference",
                    "interaction": [ { "code": "read" }, { "code": "search-type" } ],
                    "searchParam": [ { "name": "patient", "type": "reference" } ] }
                ]
              } ]
            }
            """;

    private final HttpServer http;
    private final ExecutorService pool;
    private final InMemoryStore store;
    private final CdaBridge bridge;
    private final String baseUrl;

    /**
     * @param port          puerto de escucha; 0 elige uno libre
     * @param publicBaseUrl URL pública usada en los enlaces; si es null, http://localhost:{puerto}
     */
    public FhirFacadeServer(int port, String publicBaseUrl, InMemoryStore store) throws IOException {
        this.store = store;
        this.http = HttpServer.create(new InetSocketAddress(port), 0);
        this.pool = Executors.newFixedThreadPool(8);
        this.http.setExecutor(pool);

        String base = publicBaseUrl;
        if (base == null || base.isBlank()) {
            base = "http://localhost:" + http.getAddress().getPort();
        }
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.bridge = new CdaBridge(store, this.baseUrl);

        http.createContext("/fhir/metadata", guarded(this::metadata));
        http.createContext("/fhir/Patient", guarded(this::patients));
        http.createContext("/fhir/DocumentReference", guarded(this::documentReferences));
        http.createContext("/documents/", guarded(this::documents));
        http.createContext("/bridge/cda", guarded(this::ingestHandler));
    }

    public void start() {
        http.start();
    }

    public int port() {
        return http.getAddress().getPort();
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** Ingesta directa (usada al cargar el ejemplo y en los tests). */
    public CdaBridge.Result ingest(byte[] cda) throws CdaParseException {
        return bridge.ingest(cda);
    }

    @Override
    public void close() {
        http.stop(0);
        pool.shutdownNow();
    }

    // ---- handlers -------------------------------------------------------------------------

    private void metadata(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) {
            return;
        }
        sendBytes(ex, 200, FHIR_JSON, CAPABILITY_STATEMENT.getBytes(StandardCharsets.UTF_8));
    }

    private void patients(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) {
            return;
        }
        String rest = ex.getRequestURI().getPath().substring("/fhir/Patient".length());
        if (!rest.isEmpty() && !rest.startsWith("/")) {
            notFound(ex, ex.getRequestURI().getPath());
            return;
        }
        if (rest.isEmpty() || "/".equals(rest)) {
            String identifier = query(ex.getRequestURI()).get("identifier");
            List<Patient> found =
                    identifier == null ? store.allPatients() : store.patientsByIdentifier(identifier);
            sendResource(ex, 200, searchBundle(found));
            return;
        }
        String id = rest.substring(1);
        Optional<Patient> patient = store.patient(id);
        if (patient.isPresent()) {
            sendResource(ex, 200, patient.get());
        } else {
            notFound(ex, "Patient/" + id);
        }
    }

    private void documentReferences(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) {
            return;
        }
        String rest = ex.getRequestURI().getPath().substring("/fhir/DocumentReference".length());
        if (!rest.isEmpty() && !rest.startsWith("/")) {
            notFound(ex, ex.getRequestURI().getPath());
            return;
        }
        if (rest.isEmpty() || "/".equals(rest)) {
            String patient = query(ex.getRequestURI()).get("patient");
            List<DocumentReference> found;
            if (patient == null) {
                found = store.allDocuments();
            } else {
                String patientId = patient.startsWith("Patient/") ? patient.substring(8) : patient;
                found = store.documentsByPatient(patientId);
            }
            sendResource(ex, 200, searchBundle(found));
            return;
        }
        String id = rest.substring(1);
        Optional<DocumentReference> doc = store.document(id);
        if (doc.isPresent()) {
            sendResource(ex, 200, doc.get());
        } else {
            notFound(ex, "DocumentReference/" + id);
        }
    }

    private void documents(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) {
            return;
        }
        String id = ex.getRequestURI().getPath().substring("/documents/".length());
        Optional<byte[]> cda = store.cda(id);
        if (cda.isPresent()) {
            sendBytes(ex, 200, "text/xml; charset=utf-8", cda.get());
        } else {
            notFound(ex, "documents/" + id);
        }
    }

    private void ingestHandler(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) {
            return;
        }
        byte[] body = ex.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            sendResource(ex, 413, outcome(OperationOutcome.IssueType.TOOLONG,
                    "El documento supera el máximo de " + MAX_BODY_BYTES + " bytes"));
            return;
        }
        try {
            CdaBridge.Result result = bridge.ingest(body);
            DocumentReference created = store.document(result.documentId()).orElseThrow();
            ex.getResponseHeaders()
                    .add("Location", baseUrl + "/fhir/DocumentReference/" + result.documentId());
            sendResource(ex, 201, created);
        } catch (CdaParseException e) {
            sendResource(ex, 400, outcome(OperationOutcome.IssueType.INVALID, e.getMessage()));
        }
    }

    // ---- utilidades -----------------------------------------------------------------------

    private HttpHandler guarded(HttpHandler handler) {
        return ex -> {
            try {
                handler.handle(ex);
            } catch (Exception e) {
                sendResource(ex, 500,
                        outcome(OperationOutcome.IssueType.EXCEPTION, "Error interno del servidor"));
            } finally {
                ex.close();
            }
        };
    }

    private boolean requireMethod(HttpExchange ex, String method) throws IOException {
        if (method.equals(ex.getRequestMethod())) {
            return true;
        }
        ex.getResponseHeaders().add("Allow", method);
        sendResource(ex, 405, outcome(OperationOutcome.IssueType.NOTSUPPORTED,
                "Método no soportado: " + ex.getRequestMethod()));
        return false;
    }

    private void notFound(HttpExchange ex, String what) throws IOException {
        sendResource(ex, 404, outcome(OperationOutcome.IssueType.NOTFOUND, "No existe " + what));
    }

    private Bundle searchBundle(List<? extends Resource> items) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal(items.size());
        for (Resource r : items) {
            bundle.addEntry()
                    .setFullUrl(baseUrl + "/fhir/" + r.fhirType() + "/" + r.getIdElement().getIdPart())
                    .setResource(r);
        }
        return bundle;
    }

    private static OperationOutcome outcome(OperationOutcome.IssueType type, String message) {
        OperationOutcome oo = new OperationOutcome();
        oo.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(type)
                .setDiagnostics(message);
        return oo;
    }

    private static void sendResource(HttpExchange ex, int status, IBaseResource resource)
            throws IOException {
        String json = CTX.newJsonParser().setPrettyPrint(true).encodeResourceToString(resource);
        sendBytes(ex, status, FHIR_JSON, json.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(HttpExchange ex, int status, String contentType, byte[] body)
            throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> params = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(
                        URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }
}
