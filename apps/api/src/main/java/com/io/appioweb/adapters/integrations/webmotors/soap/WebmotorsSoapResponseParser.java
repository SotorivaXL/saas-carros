package com.io.appioweb.adapters.integrations.webmotors.soap;

import com.io.appioweb.domain.ioauto.webmotors.WebmotorsCatalogEntry;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsInventoryItem;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsInventoryPage;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsSoapAuthResult;
import com.io.appioweb.domain.ioauto.webmotors.WebmotorsSoapOperationResult;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class WebmotorsSoapResponseParser {

    public WebmotorsSoapAuthResult parseAuth(String xml) {
        Document document = parse(xml);
        return new WebmotorsSoapAuthResult(
                firstText(document, "HashAutenticacao"),
                firstText(document, "CodigoRetorno"),
                firstNonBlank(firstText(document, "RequestId"), firstText(document, "RequestID"))
        );
    }

    public WebmotorsInventoryPage parseInventoryPage(String xml) {
        Document document = parse(xml);
        List<WebmotorsInventoryItem> anuncios = new ArrayList<>();
        for (Element anuncio : elements(document, "Anuncio")) {
            anuncios.add(new WebmotorsInventoryItem(
                    firstText(anuncio, "CodigoAnuncio"),
                    firstText(anuncio, "CodigoMarca"),
                    firstText(anuncio, "CodigoModelo"),
                    firstText(anuncio, "CodigoVersao"),
                    firstNonBlank(firstText(anuncio, "Titulo"), firstText(anuncio, "Descricao")),
                    parseLong(firstNonBlank(firstText(anuncio, "PrecoVenda"), firstText(anuncio, "Preco"))),
                    parseInteger(firstNonBlank(firstText(anuncio, "Quilometragem"), firstText(anuncio, "Km"))),
                    firstNonBlank(firstText(anuncio, "Status"), firstText(anuncio, "Situacao")),
                    firstText(anuncio, "DataInclusao"),
                    firstNonBlank(firstText(anuncio, "DataUltimaAlteracao"), firstText(anuncio, "DataAlteracao")),
                    xmlFragment(anuncio)
            ));
        }
        return new WebmotorsInventoryPage(
                parseInteger(firstText(document, "Pagina"), 1),
                parseInteger(firstText(document, "AnunciosPorPagina"), anuncios.size()),
                parseInteger(firstText(document, "TotalAnuncios"), anuncios.size()),
                firstText(document, "CodigoRetorno"),
                anuncios,
                firstNonBlank(firstText(document, "RequestId"), firstText(document, "RequestID"))
        );
    }

    public WebmotorsSoapOperationResult parseOperationResult(String xml) {
        Document document = parse(xml);
        return new WebmotorsSoapOperationResult(
                firstText(document, "CodigoRetorno"),
                firstNonBlank(firstText(document, "RequestId"), firstText(document, "RequestID")),
                firstNonBlank(firstText(document, "CodigoAnuncio"), firstText(document, "Codigo")),
                firstNonBlank(firstText(document, "Status"), firstText(document, "Situacao")),
                xml
        );
    }

    public List<WebmotorsCatalogEntry> parseCatalog(String xml, String type) {
        Document document = parse(xml);
        List<WebmotorsCatalogEntry> items = new ArrayList<>();
        List<Element> nodes = elements(document, "Item");
        if (nodes.isEmpty()) {
            nodes = elements(document, "Catalogo");
        }
        for (Element item : nodes) {
            String internalValue = firstNonBlank(firstText(item, "Descricao"), firstText(item, "Nome"), firstText(item, "Label"));
            String code = firstNonBlank(firstText(item, "Codigo"), firstText(item, "Id"), firstText(item, "Valor"));
            if (internalValue.isBlank() || code.isBlank()) {
                continue;
            }
            items.add(new WebmotorsCatalogEntry(type, internalValue, code, internalValue, xmlFragment(item)));
        }
        return items;
    }

    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml == null ? "" : xml)));
            document.getDocumentElement().normalize();
            return document;
        } catch (Exception exception) {
            throw new BusinessException("WEBMOTORS_SOAP_RESPONSE_PARSE_FAILED", "Não foi possível interpretar a resposta SOAP da Webmotors.");
        }
    }

    private List<Element> elements(Document document, String localName) {
        return elements(document.getDocumentElement(), localName);
    }

    private List<Element> elements(Element root, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = root.getElementsByTagNameNS("*", localName);
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private String firstText(Document document, String localName) {
        List<Element> elements = elements(document, localName);
        return elements.isEmpty() ? "" : safe(elements.get(0).getTextContent());
    }

    private String firstText(Element element, String localName) {
        NodeList nodes = element.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return safe(nodes.item(0).getTextContent());
    }

    private String xmlFragment(Element element) {
        return safe(element.getTextContent());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = safe(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private Integer parseInteger(String value) {
        return parseInteger(value, 0);
    }

    private Integer parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(safe(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(safe(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
