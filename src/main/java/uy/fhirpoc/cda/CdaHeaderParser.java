package uy.fhirpoc.cda;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Lee la cabecera de un CDA R2 con el DOM del JDK (sin dependencias externas).
 * El parser rechaza DOCTYPE y entidades externas (protección contra XXE).
 */
public final class CdaHeaderParser {

    private static final String NS = "urn:hl7-org:v3";

    private CdaHeaderParser() {
    }

    public static CdaHeader parse(byte[] xml) throws CdaParseException {
        Element root = readRoot(xml);
        if (!"ClinicalDocument".equals(root.getLocalName()) || !NS.equals(root.getNamespaceURI())) {
            throw new CdaParseException("El elemento raíz no es un ClinicalDocument HL7 v3");
        }

        Element docId = child(root, "id");
        String docRoot = attr(docId, "root");
        if (docRoot == null) {
            throw new CdaParseException("Falta ClinicalDocument/id con atributo root");
        }

        Element patientRole = path(root, "recordTarget", "patientRole");
        Element patientId = child(patientRole, "id");
        String patientRoot = attr(patientId, "root");
        String patientExt = attr(patientId, "extension");
        if (patientRoot == null || patientExt == null) {
            throw new CdaParseException(
                    "Falta recordTarget/patientRole/id con atributos root y extension");
        }

        Element patient = child(patientRole, "patient");
        Element patientName = child(patient, "name");
        Element code = child(root, "code");

        return new CdaHeader(
                docRoot,
                attr(docId, "extension"),
                attr(code, "code"),
                attr(code, "codeSystem"),
                attr(code, "displayName"),
                text(child(root, "title")),
                attr(child(root, "effectiveTime"), "value"),
                attr(child(root, "confidentialityCode"), "code"),
                attr(child(root, "languageCode"), "code"),
                patientRoot,
                patientExt,
                text(child(patientName, "given")),
                text(child(patientName, "family")),
                attr(child(patient, "administrativeGenderCode"), "code"),
                attr(child(patient, "birthTime"), "value"),
                personName(path(root, "author", "assignedAuthor", "assignedPerson", "name")),
                text(path(root, "custodian", "assignedCustodian",
                        "representedCustodianOrganization", "name")));
    }

    private static Element readRoot(byte[] xml) throws CdaParseException {
        if (xml == null || xml.length == 0) {
            throw new CdaParseException("El cuerpo está vacío");
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder builder = f.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler()); // evita imprimir errores en stderr
            Document doc = builder.parse(new ByteArrayInputStream(xml));
            return doc.getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new CdaParseException("XML inválido o no permitido: " + e.getMessage(), e);
        }
    }

    private static Element child(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE
                    && NS.equals(n.getNamespaceURI())
                    && localName.equals(n.getLocalName())) {
                return (Element) n;
            }
        }
        return null;
    }

    private static Element path(Element start, String... names) {
        Element current = start;
        for (String name : names) {
            current = child(current, name);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static String attr(Element e, String name) {
        if (e == null) {
            return null;
        }
        String v = e.getAttribute(name);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String text(Element e) {
        if (e == null) {
            return null;
        }
        String v = e.getTextContent();
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String personName(Element name) {
        if (name == null) {
            return null;
        }
        String given = text(child(name, "given"));
        String family = text(child(name, "family"));
        if (given == null && family == null) {
            return text(name);
        }
        if (given == null) {
            return family;
        }
        return family == null ? given : given + " " + family;
    }
}
