package uy.fhirpoc.xds;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import uy.fhirpoc.cda.CdaHeader;
import uy.fhirpoc.util.Ids;

/**
 * Subconjunto de los metadatos de un DocumentEntry de IHE XDS.b, tal como los
 * necesita la PoC para publicar el documento como DocumentReference FHIR.
 *
 * <p>En esta PoC los metadatos se derivan de la cabecera CDA. En un despliegue real
 * llegarían del registro XDS (transacciones ITI-18 / ITI-42).
 */
public record XdsDocumentEntry(
        String uniqueIdRoot,
        String uniqueIdValue,
        String entryUuid,
        String mimeType,
        String classCode,
        String classCodeSystem,
        String classDisplay,
        String typeCode,
        String typeCodeSystem,
        String typeDisplay,
        String formatCode,
        String confidentialityCode,
        String languageCode,
        String creationTime,
        String patientResourceId,
        String title,
        long size,
        byte[] sha1,
        String uri,
        String authorName,
        String custodianName) {

    /** Código de formato IHE para "el tipo MIME alcanza para interpretar el documento". */
    public static final String FORMAT_MIME_TYPE_SUFFICIENT = "urn:ihe:iti:xds:2017:mimeTypeSufficient";

    /**
     * Deriva el DocumentEntry a partir de la cabecera CDA. Simplificación de la PoC:
     * classCode se toma igual al typeCode (el XDS real usa un value set de clases aparte).
     */
    public static XdsDocumentEntry fromCda(
            CdaHeader h, byte[] cdaBytes, String patientResourceId, String uri) {
        String uniqueValue = h.documentExtension() != null ? h.documentExtension() : h.documentRoot();
        String entryUuid = "urn:uuid:" + Ids.stable("entry", h.documentRoot(), uniqueValue);
        return new XdsDocumentEntry(
                h.documentRoot(),
                uniqueValue,
                entryUuid,
                "text/xml",
                h.typeCode(),
                h.typeCodeSystem(),
                h.typeDisplay(),
                h.typeCode(),
                h.typeCodeSystem(),
                h.typeDisplay(),
                FORMAT_MIME_TYPE_SUFFICIENT,
                h.confidentialityCode(),
                h.languageCode(),
                h.effectiveTime(),
                patientResourceId,
                h.title(),
                cdaBytes.length,
                sha1(cdaBytes),
                uri,
                h.authorName(),
                h.custodianName());
    }

    private static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 no disponible en la JVM", e);
        }
    }
}
