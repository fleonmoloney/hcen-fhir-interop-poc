package uy.fhirpoc.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.context.FhirContext;
import uy.fhirpoc.bridge.CdaBridge;
import uy.fhirpoc.cda.CdaHeaderParserTest;
import uy.fhirpoc.store.InMemoryStore;

class FhirFacadeServerTest {

    private static final FhirContext CTX = FhirContext.forR4Cached();

    private FhirFacadeServer server;
    private HttpClient client;
    private String base;
    private CdaBridge.Result loaded;

    @BeforeEach
    void setUp() throws Exception {
        server = new FhirFacadeServer(0, null, new InMemoryStore());
        loaded = server.ingest(CdaHeaderParserTest.sample());
        server.start();
        client = HttpClient.newHttpClient();
        base = server.baseUrl();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, byte[] body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void metadataEsUnCapabilityStatementR4() throws Exception {
        HttpResponse<String> r = get("/fhir/metadata");
        assertEquals(200, r.statusCode());
        CapabilityStatement cs = CTX.newJsonParser().parseResource(CapabilityStatement.class, r.body());
        assertEquals("4.0.1", cs.getFhirVersion().toCode());
        assertEquals(2, cs.getRestFirstRep().getResource().size());
    }

    @Test
    void leeElPaciente() throws Exception {
        HttpResponse<String> r = get("/fhir/Patient/" + loaded.patientId());
        assertEquals(200, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/fhir+json"));
        Patient p = CTX.newJsonParser().parseResource(Patient.class, r.body());
        assertEquals("Ejemplo", p.getNameFirstRep().getFamily());
    }

    @Test
    void buscaPacientePorIdentificador() throws Exception {
        HttpResponse<String> r = get("/fhir/Patient?identifier=EJ-0001");
        assertEquals(200, r.statusCode());
        Bundle b = CTX.newJsonParser().parseResource(Bundle.class, r.body());
        assertEquals(1, b.getTotal());

        Bundle none = CTX.newJsonParser().parseResource(Bundle.class,
                get("/fhir/Patient?identifier=NO-EXISTE").body());
        assertEquals(0, none.getTotal());
    }

    @Test
    void buscaDocumentosPorPaciente() throws Exception {
        HttpResponse<String> r = get("/fhir/DocumentReference?patient=Patient/" + loaded.patientId());
        assertEquals(200, r.statusCode());
        Bundle b = CTX.newJsonParser().parseResource(Bundle.class, r.body());
        assertEquals(1, b.getTotal());
        DocumentReference d = (DocumentReference) b.getEntryFirstRep().getResource();
        assertEquals(base + "/documents/" + loaded.documentId(),
                d.getContentFirstRep().getAttachment().getUrl());
    }

    @Test
    void sirveElCdaOriginal() throws Exception {
        HttpResponse<String> r = get("/documents/" + loaded.documentId());
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("ClinicalDocument"));
    }

    @Test
    void recursoInexistenteDaOperationOutcome404() throws Exception {
        HttpResponse<String> r = get("/fhir/Patient/no-existe");
        assertEquals(404, r.statusCode());
        OperationOutcome oo = CTX.newJsonParser().parseResource(OperationOutcome.class, r.body());
        assertEquals(OperationOutcome.IssueType.NOTFOUND, oo.getIssueFirstRep().getCode());
    }

    @Test
    void rutaConPrefijoSimilarNoSeInterpretaComoId() throws Exception {
        assertEquals(404, get("/fhir/PatientX").statusCode());
    }

    @Test
    void ingestaPorHttpEsIdempotente() throws Exception {
        HttpResponse<String> r = post("/bridge/cda", CdaHeaderParserTest.sample());
        assertEquals(201, r.statusCode());
        assertTrue(r.headers().firstValue("Location").orElse("")
                .endsWith("/fhir/DocumentReference/" + loaded.documentId()));

        Bundle all = CTX.newJsonParser().parseResource(Bundle.class, get("/fhir/DocumentReference").body());
        assertEquals(1, all.getTotal());
    }

    @Test
    void ingestaDeUnDocumentoInvalidoDa400() throws Exception {
        HttpResponse<String> r = post("/bridge/cda", "<x/>".getBytes());
        assertEquals(400, r.statusCode());
    }

    @Test
    void metodoNoSoportadoDa405() throws Exception {
        HttpResponse<String> r = post("/fhir/metadata", new byte[0]);
        assertEquals(405, r.statusCode());
    }
}
