package uy.fhirpoc.fhir;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;

import uy.fhirpoc.cda.CdaHeader;
import uy.fhirpoc.util.Hl7Time;
import uy.fhirpoc.xds.XdsDocumentEntry;

/**
 * Traduce la cabecera CDA a Patient y los metadatos XDS.b a DocumentReference (FHIR R4).
 * El mapeo de DocumentEntry sigue la lógica de IHE MHD, pero esta clase no pretende ser
 * una implementación conforme del perfil.
 */
public final class FhirMapper {

    private static final String V3_CONFIDENTIALITY =
            "http://terminology.hl7.org/CodeSystem/v3-Confidentiality";
    private static final String IHE_FORMAT_CODE =
            "http://ihe.net/fhir/ValueSet/IHE.FormatCode.codesystem";

    /** OID de sistemas de códigos habituales -> URI canónica FHIR. */
    private static final Map<String, String> CODE_SYSTEMS = Map.of(
            "2.16.840.1.113883.6.1", "http://loinc.org",
            "2.16.840.1.113883.6.96", "http://snomed.info/sct");

    private FhirMapper() {
    }

    public static Patient toPatient(CdaHeader h, String id) {
        Patient p = new Patient();
        p.setId(id);
        p.addIdentifier().setSystem("urn:oid:" + h.patientRoot()).setValue(h.patientExtension());

        if (h.familyName() != null || h.givenName() != null) {
            HumanName name = p.addName();
            name.setUse(HumanName.NameUse.OFFICIAL);
            if (h.familyName() != null) {
                name.setFamily(h.familyName());
            }
            if (h.givenName() != null) {
                name.addGiven(h.givenName());
            }
        }

        p.setGender(toGender(h.genderCode()));

        String birth = Hl7Time.toIsoDate(h.birthTime());
        if (birth != null) {
            p.setBirthDateElement(new DateType(birth));
        }
        return p;
    }

    public static DocumentReference toDocumentReference(XdsDocumentEntry e, String id) {
        DocumentReference d = new DocumentReference();
        d.setId(id);

        // XDS uniqueId -> masterIdentifier; entryUUID -> identifier
        d.setMasterIdentifier(
                new Identifier().setSystem("urn:oid:" + e.uniqueIdRoot()).setValue(e.uniqueIdValue()));
        d.addIdentifier(new Identifier().setSystem("urn:ietf:rfc:3986").setValue(e.entryUuid()));
        d.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);

        if (e.typeCode() != null) {
            d.setType(concept(e.typeCodeSystem(), e.typeCode(), e.typeDisplay()));
        }
        if (e.classCode() != null) {
            d.addCategory(concept(e.classCodeSystem(), e.classCode(), e.classDisplay()));
        }

        d.setSubject(new Reference("Patient/" + e.patientResourceId()));

        Instant created = Hl7Time.parseInstant(e.creationTime());
        if (created != null) {
            d.setDate(Date.from(created));
        }
        if (e.authorName() != null) {
            d.addAuthor(new Reference().setDisplay(e.authorName()));
        }
        if (e.custodianName() != null) {
            d.setCustodian(new Reference().setDisplay(e.custodianName()));
        }
        if (e.confidentialityCode() != null) {
            d.addSecurityLabel(new CodeableConcept()
                    .addCoding(new Coding(V3_CONFIDENTIALITY, e.confidentialityCode(), null)));
        }

        Attachment attachment = new Attachment()
                .setContentType(e.mimeType())
                .setUrl(e.uri())
                .setSize((int) e.size())
                .setHash(e.sha1());
        if (e.title() != null) {
            attachment.setTitle(e.title());
        }
        if (e.languageCode() != null) {
            attachment.setLanguage(e.languageCode());
        }
        DocumentReference.DocumentReferenceContentComponent content =
                d.addContent().setAttachment(attachment);
        if (e.formatCode() != null) {
            content.setFormat(new Coding(IHE_FORMAT_CODE, e.formatCode(), null));
        }
        return d;
    }

    static String systemFor(String codeSystemOid) {
        if (codeSystemOid == null) {
            return null;
        }
        return CODE_SYSTEMS.getOrDefault(codeSystemOid, "urn:oid:" + codeSystemOid);
    }

    private static CodeableConcept concept(String oid, String code, String display) {
        return new CodeableConcept().addCoding(new Coding(systemFor(oid), code, display));
    }

    private static Enumerations.AdministrativeGender toGender(String code) {
        if (code == null) {
            return Enumerations.AdministrativeGender.UNKNOWN;
        }
        switch (code) {
            case "F":
                return Enumerations.AdministrativeGender.FEMALE;
            case "M":
                return Enumerations.AdministrativeGender.MALE;
            case "UN":
                return Enumerations.AdministrativeGender.OTHER;
            default:
                return Enumerations.AdministrativeGender.UNKNOWN;
        }
    }
}
