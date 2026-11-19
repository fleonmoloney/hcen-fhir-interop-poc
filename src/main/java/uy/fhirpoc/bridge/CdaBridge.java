package uy.fhirpoc.bridge;

import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Patient;

import uy.fhirpoc.cda.CdaHeader;
import uy.fhirpoc.cda.CdaHeaderParser;
import uy.fhirpoc.cda.CdaParseException;
import uy.fhirpoc.fhir.FhirMapper;
import uy.fhirpoc.store.InMemoryStore;
import uy.fhirpoc.util.Ids;
import uy.fhirpoc.xds.XdsDocumentEntry;

/**
 * Puente entre el mundo CDA/XDS.b y FHIR: recibe un CDA, deriva sus metadatos XDS.b
 * y publica un Patient y un DocumentReference que apuntan al documento original.
 * El documento CDA no se modifica ni se reemplaza: FHIR se agrega al lado.
 */
public final class CdaBridge {

    public record Result(String patientId, String documentId) {
    }

    private final InMemoryStore store;
    private final String baseUrl;

    public CdaBridge(InMemoryStore store, String baseUrl) {
        this.store = store;
        this.baseUrl = baseUrl;
    }

    /** Idempotente: reenviar el mismo documento produce los mismos ids y no duplica nada. */
    public Result ingest(byte[] cda) throws CdaParseException {
        CdaHeader header = CdaHeaderParser.parse(cda);

        String patientId = Ids.stable("patient", header.patientRoot(), header.patientExtension());
        String docKey = header.documentExtension() != null ? header.documentExtension() : "";
        String documentId = Ids.stable("document", header.documentRoot(), docKey);

        Patient patient = FhirMapper.toPatient(header, patientId);
        XdsDocumentEntry entry =
                XdsDocumentEntry.fromCda(header, cda, patientId, baseUrl + "/documents/" + documentId);
        DocumentReference reference = FhirMapper.toDocumentReference(entry, documentId);

        store.put(patient);
        store.put(reference, cda);
        return new Result(patientId, documentId);
    }
}
