package uy.fhirpoc.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;

/** Almacén en memoria de la PoC: se pierde al reiniciar el proceso. */
public final class InMemoryStore {

    private final Map<String, Patient> patients = new ConcurrentHashMap<>();
    private final Map<String, DocumentReference> documents = new ConcurrentHashMap<>();
    private final Map<String, byte[]> cdaBodies = new ConcurrentHashMap<>();

    public void put(Patient p) {
        patients.put(p.getIdElement().getIdPart(), p);
    }

    public void put(DocumentReference d, byte[] cda) {
        String id = d.getIdElement().getIdPart();
        documents.put(id, d);
        cdaBodies.put(id, cda);
    }

    public Optional<Patient> patient(String id) {
        return Optional.ofNullable(patients.get(id));
    }

    public Optional<DocumentReference> document(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    public Optional<byte[]> cda(String id) {
        return Optional.ofNullable(cdaBodies.get(id));
    }

    public List<Patient> allPatients() {
        return new ArrayList<>(patients.values());
    }

    public List<DocumentReference> allDocuments() {
        return new ArrayList<>(documents.values());
    }

    /** Acepta "valor" o "sistema|valor", como el parámetro de búsqueda token de FHIR. */
    public List<Patient> patientsByIdentifier(String token) {
        String system = null;
        String value = token;
        int bar = token.indexOf('|');
        if (bar >= 0) {
            system = token.substring(0, bar);
            value = token.substring(bar + 1);
        }
        List<Patient> result = new ArrayList<>();
        for (Patient p : patients.values()) {
            for (Identifier id : p.getIdentifier()) {
                boolean systemMatches = system == null || system.isEmpty() || system.equals(id.getSystem());
                if (systemMatches && value.equals(id.getValue())) {
                    result.add(p);
                    break;
                }
            }
        }
        return result;
    }

    public List<DocumentReference> documentsByPatient(String patientId) {
        String ref = "Patient/" + patientId;
        List<DocumentReference> result = new ArrayList<>();
        for (DocumentReference d : documents.values()) {
            if (ref.equals(d.getSubject().getReference())) {
                result.add(d);
            }
        }
        return result;
    }
}
