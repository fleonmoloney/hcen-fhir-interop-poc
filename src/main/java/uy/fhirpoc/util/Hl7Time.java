package uy.fhirpoc.util;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversión de marcas de tiempo HL7 v3 / XDS (yyyyMMddHHmmss[+-hhmm]) a tipos de Java.
 * Si la marca no trae zona horaria se interpreta como UTC (supuesto de la PoC).
 */
public final class Hl7Time {

    private static final Pattern TS = Pattern.compile(
            "^(\\d{4})(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(?:\\.\\d+)?([+-]\\d{4})?$");
    private static final Pattern DATE = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2}).*$");

    private Hl7Time() {
    }

    /** Devuelve el instante o null si la marca es nula o no tiene un formato válido. */
    public static Instant parseInstant(String ts) {
        if (ts == null) {
            return null;
        }
        Matcher m = TS.matcher(ts.trim());
        if (!m.matches()) {
            return null;
        }
        try {
            ZoneOffset offset = ZoneOffset.UTC;
            if (m.group(7) != null) {
                String z = m.group(7);
                offset = ZoneOffset.of(z.substring(0, 3) + ":" + z.substring(3));
            }
            return LocalDateTime.of(
                    Integer.parseInt(m.group(1)),
                    num(m.group(2), 1),
                    num(m.group(3), 1),
                    num(m.group(4), 0),
                    num(m.group(5), 0),
                    num(m.group(6), 0)).toInstant(offset);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Devuelve yyyy-MM-dd a partir de una marca que empiece con yyyyMMdd, o null. */
    public static String toIsoDate(String ts) {
        if (ts == null) {
            return null;
        }
        Matcher m = DATE.matcher(ts.trim());
        if (!m.matches()) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3))).toString();
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static int num(String s, int dflt) {
        return s == null ? dflt : Integer.parseInt(s);
    }
}
