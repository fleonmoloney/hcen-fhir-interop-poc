package uy.fhirpoc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class Hl7TimeTest {

    @Test
    void respetaLaZonaHoraria() {
        assertEquals(Instant.parse("2026-11-10T12:30:00Z"), Hl7Time.parseInstant("20261110093000-0300"));
    }

    @Test
    void sinZonaSeInterpretaComoUtc() {
        assertEquals(Instant.parse("2026-11-10T09:30:00Z"), Hl7Time.parseInstant("20261110093000"));
    }

    @Test
    void aceptaPrecisionReducida() {
        assertEquals(Instant.parse("2026-11-10T00:00:00Z"), Hl7Time.parseInstant("20261110"));
    }

    @Test
    void valoresInvalidosDanNull() {
        assertNull(Hl7Time.parseInstant(null));
        assertNull(Hl7Time.parseInstant("no-es-fecha"));
        assertNull(Hl7Time.parseInstant("20261340"));
    }

    @Test
    void fechaIso() {
        assertEquals("1980-02-15", Hl7Time.toIsoDate("19800215"));
        assertEquals("1980-02-15", Hl7Time.toIsoDate("19800215120000"));
        assertNull(Hl7Time.toIsoDate("1980"));
        assertNull(Hl7Time.toIsoDate("19801345"));
    }

    @Test
    void idsEstables() {
        assertEquals(Ids.stable("a", "b"), Ids.stable("a", "b"));
    }
}
