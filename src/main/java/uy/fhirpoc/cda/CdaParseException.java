package uy.fhirpoc.cda;

/** El documento recibido no es un CDA R2 utilizable por la PoC. */
public class CdaParseException extends Exception {

    private static final long serialVersionUID = 1L;

    public CdaParseException(String message) {
        super(message);
    }

    public CdaParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
