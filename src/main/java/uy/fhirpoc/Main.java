package uy.fhirpoc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import uy.fhirpoc.server.FhirFacadeServer;
import uy.fhirpoc.store.InMemoryStore;

/**
 * Arranque de la PoC.
 *
 * <p>Variables de entorno: PORT (8080), PUBLIC_BASE_URL (enlaces absolutos),
 * LOAD_SAMPLE=true (equivale a pasar --load-sample).
 */
public final class Main {

    private static final String SAMPLE = "/samples/informe-alta-ficticio.xml";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String publicBaseUrl = System.getenv("PUBLIC_BASE_URL");
        boolean loadSample = Arrays.asList(args).contains("--load-sample")
                || "true".equalsIgnoreCase(System.getenv("LOAD_SAMPLE"));

        FhirFacadeServer server = new FhirFacadeServer(port, publicBaseUrl, new InMemoryStore());
        if (loadSample) {
            try (InputStream in = Main.class.getResourceAsStream(SAMPLE)) {
                if (in == null) {
                    throw new IOException("No se encontró el recurso " + SAMPLE);
                }
                var result = server.ingest(in.readAllBytes());
                System.out.println("Ejemplo cargado: Patient/" + result.patientId()
                        + " DocumentReference/" + result.documentId());
            }
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.println("Fachada FHIR escuchando en " + server.baseUrl() + "/fhir");
    }
}
