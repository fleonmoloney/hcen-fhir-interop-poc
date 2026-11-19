package uy.fhirpoc.cda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class CdaHeaderParserTest {

    public static byte[] sample() throws IOException {
        try (InputStream in = CdaHeaderParserTest.class.getResourceAsStream("/samples/informe-alta-ficticio.xml")) {
            return in.readAllBytes();
        }
    }

    @Test
    void parseaLaCabeceraDelEjemplo() throws Exception {
        CdaHeader h = CdaHeaderParser.parse(sample());

        assertEquals("2.25.100000000000000000000000000000002", h.documentRoot());
        assertEquals("INF-0001", h.documentExtension());
        assertEquals("18842-5", h.typeCode());
        assertEquals("2.16.840.1.113883.6.1", h.typeCodeSystem());
        assertEquals("Informe de alta", h.title());
        assertEquals("20261110093000-0300", h.effectiveTime());
        assertEquals("N", h.confidentialityCode());
        assertEquals("es-UY", h.languageCode());
        assertEquals("EJ-0001", h.patientExtension());
        assertEquals("Ana", h.givenName());
        assertEquals("Ejemplo", h.familyName());
        assertEquals("F", h.genderCode());
        assertEquals("19800215", h.birthTime());
        assertEquals("Luis Prueba", h.authorName());
        assertEquals("Hospital Ficticio", h.custodianName());
    }

    @Test
    void rechazaDoctypeParaEvitarXxe() {
        byte[] xml = ("<?xml version=\"1.0\"?><!DOCTYPE a [<!ENTITY x \"y\">]>"
                + "<ClinicalDocument xmlns=\"urn:hl7-org:v3\"/>").getBytes(StandardCharsets.UTF_8);
        assertThrows(CdaParseException.class, () -> CdaHeaderParser.parse(xml));
    }

    @Test
    void rechazaUnaRaizQueNoEsCda() {
        byte[] xml = "<otra xmlns=\"urn:hl7-org:v3\"/>".getBytes(StandardCharsets.UTF_8);
        assertThrows(CdaParseException.class, () -> CdaHeaderParser.parse(xml));
    }

    @Test
    void exigeElIdentificadorDelPaciente() {
        byte[] xml = ("<ClinicalDocument xmlns=\"urn:hl7-org:v3\"><id root=\"1.2.3\"/></ClinicalDocument>")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(CdaParseException.class, () -> CdaHeaderParser.parse(xml));
    }

    @Test
    void rechazaCuerpoVacio() {
        assertThrows(CdaParseException.class, () -> CdaHeaderParser.parse(new byte[0]));
    }

    @Test
    void losCamposOpcionalesAusentesQuedanEnNull() throws Exception {
        byte[] xml = ("<ClinicalDocument xmlns=\"urn:hl7-org:v3\"><id root=\"1.2.3\"/>"
                + "<recordTarget><patientRole><id root=\"4.5.6\" extension=\"P1\"/></patientRole></recordTarget>"
                + "</ClinicalDocument>").getBytes(StandardCharsets.UTF_8);
        CdaHeader h = CdaHeaderParser.parse(xml);
        assertNull(h.givenName());
        assertNull(h.authorName());
        assertNull(h.documentExtension());
    }
}
