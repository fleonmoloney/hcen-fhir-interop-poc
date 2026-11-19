package uy.fhirpoc.fhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

import uy.fhirpoc.cda.CdaHeader;
import uy.fhirpoc.cda.CdaHeaderParser;
import uy.fhirpoc.cda.CdaHeaderParserTest;
import uy.fhirpoc.xds.XdsDocumentEntry;

class FhirMapperTest {

    private static CdaHeader header() throws Exception {
        return CdaHeaderParser.parse(CdaHeaderParserTest.sample());
    }

    @Test
    void mapeaElPaciente() throws Exception {
        Patient p = FhirMapper.toPatient(header(), "p1");

        assertEquals("p1", p.getIdElement().getIdPart());
        assertEquals("urn:oid:2.25.100000000000000000000000000000003", p.getIdentifierFirstRep().getSystem());
        assertEquals("EJ-0001", p.getIdentifierFirstRep().getValue());
        assertEquals("Ejemplo", p.getNameFirstRep().getFamily());
        assertEquals("Ana", p.getNameFirstRep().getGivenAsSingleString());
        assertEquals(Enumerations.AdministrativeGender.FEMALE, p.getGender());
        assertEquals("1980-02-15", p.getBirthDateElement().getValueAsString());
    }

    @Test
    void mapeaElDocumentEntryDeXdsADocumentReference() throws Exception {
        byte[] cda = CdaHeaderParserTest.sample();
        XdsDocumentEntry entry = XdsDocumentEntry.fromCda(header(), cda, "p1", "http://x/documents/d1");

        DocumentReference d = FhirMapper.toDocumentReference(entry, "d1");

        assertEquals("d1", d.getIdElement().getIdPart());
        assertEquals("INF-0001", d.getMasterIdentifier().getValue());
        assertEquals("urn:oid:2.25.100000000000000000000000000000002", d.getMasterIdentifier().getSystem());
        assertTrue(d.getIdentifierFirstRep().getValue().startsWith("urn:uuid:"));
        assertEquals(Enumerations.DocumentReferenceStatus.CURRENT, d.getStatus());
        assertEquals("http://loinc.org", d.getType().getCodingFirstRep().getSystem());
        assertEquals("18842-5", d.getType().getCodingFirstRep().getCode());
        assertEquals("Patient/p1", d.getSubject().getReference());
        assertEquals("Luis Prueba", d.getAuthorFirstRep().getDisplay());
        assertEquals("Hospital Ficticio", d.getCustodian().getDisplay());
        assertEquals("N", d.getSecurityLabelFirstRep().getCodingFirstRep().getCode());

        var attachment = d.getContentFirstRep().getAttachment();
        assertEquals("text/xml", attachment.getContentType());
        assertEquals("http://x/documents/d1", attachment.getUrl());
        assertEquals(cda.length, attachment.getSize());
        assertEquals(20, attachment.getHash().length); // SHA-1
        assertEquals("es-UY", attachment.getLanguage());
        assertEquals(XdsDocumentEntry.FORMAT_MIME_TYPE_SUFFICIENT,
                d.getContentFirstRep().getFormat().getCode());
        assertEquals("2026-11-10T12:30:00Z", d.getDate().toInstant().toString());
    }

    @Test
    void oidDesconocidoSeConvierteEnUrnOid() {
        assertEquals("urn:oid:1.2.3", FhirMapper.systemFor("1.2.3"));
        assertEquals("http://snomed.info/sct", FhirMapper.systemFor("2.16.840.1.113883.6.96"));
    }
}
