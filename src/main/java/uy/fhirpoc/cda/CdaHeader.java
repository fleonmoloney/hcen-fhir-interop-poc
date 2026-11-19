package uy.fhirpoc.cda;

/**
 * Campos de la cabecera de un ClinicalDocument CDA R2 que usa la PoC.
 * Cualquier campo opcional ausente en el documento queda en null.
 */
public record CdaHeader(
        String documentRoot,
        String documentExtension,
        String typeCode,
        String typeCodeSystem,
        String typeDisplay,
        String title,
        String effectiveTime,
        String confidentialityCode,
        String languageCode,
        String patientRoot,
        String patientExtension,
        String givenName,
        String familyName,
        String genderCode,
        String birthTime,
        String authorName,
        String custodianName) {
}
