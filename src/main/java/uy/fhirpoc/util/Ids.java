package uy.fhirpoc.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Identificadores estables: la misma entrada produce siempre el mismo id (ingesta idempotente). */
public final class Ids {

    private Ids() {
    }

    public static String stable(String... parts) {
        String joined = String.join("|", parts);
        return UUID.nameUUIDFromBytes(joined.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
