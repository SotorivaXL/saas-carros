package com.io.appioweb.adapters.integrations.webmotors.soap;

import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.Map;

@Component
public class WebmotorsSoapRequestFactory {

    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String WM_NS = "http://webmotors.com.br/";

    public String buildAuthRequest(String cnpj, String email, String senha) {
        return buildEnvelope("Autenticar", Map.of(
                "Cnpj", safe(cnpj),
                "Email", safe(email),
                "Senha", safe(senha)
        ));
    }

    public String buildInventoryPageRequest(String hashAutenticacao, int pagina, int tamanho) {
        return buildEnvelope("ObterEstoqueAtualPaginado", Map.of(
                "pHashAutenticacao", safe(hashAutenticacao),
                "pPagina", String.valueOf(Math.max(1, pagina)),
                "pTamanho", String.valueOf(Math.max(1, tamanho))
        ));
    }

    public String buildCatalogRequest(String hashAutenticacao, String tipo) {
        return buildEnvelope("ObterCatalogo", Map.of(
                "pHashAutenticacao", safe(hashAutenticacao),
                "pTipo", safe(tipo)
        ));
    }

    public String buildPublishRequest(String hashAutenticacao, Map<String, String> fields) {
        return buildEnvelope("IncluirAnuncio", withHash(hashAutenticacao, fields));
    }

    public String buildUpdateRequest(String hashAutenticacao, Map<String, String> fields) {
        return buildEnvelope("AlterarAnuncio", withHash(hashAutenticacao, fields));
    }

    public String buildDeleteRequest(String hashAutenticacao, String remoteAdCode) {
        return buildEnvelope("ExcluirAnuncio", Map.of(
                "pHashAutenticacao", safe(hashAutenticacao),
                "pCodigoAnuncio", safe(remoteAdCode)
        ));
    }

    private Map<String, String> withHash(String hashAutenticacao, Map<String, String> fields) {
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("pHashAutenticacao", safe(hashAutenticacao));
        payload.putAll(fields);
        return payload;
    }

    private String buildEnvelope(String operationName, Map<String, String> fields) {
        try {
            StringWriter buffer = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(buffer);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("soapenv", "Envelope", SOAP_NS);
            xml.writeNamespace("soapenv", SOAP_NS);
            xml.writeNamespace("wm", WM_NS);
            xml.writeStartElement("soapenv", "Header", SOAP_NS);
            xml.writeEndElement();
            xml.writeStartElement("soapenv", "Body", SOAP_NS);
            xml.writeStartElement("wm", operationName, WM_NS);
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                xml.writeStartElement("wm", entry.getKey(), WM_NS);
                xml.writeCharacters(safe(entry.getValue()));
                xml.writeEndElement();
            }
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
            xml.close();
            return buffer.toString();
        } catch (Exception exception) {
            throw new BusinessException("WEBMOTORS_SOAP_REQUEST_BUILD_FAILED", "Não foi possível montar a requisição SOAP da Webmotors.");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
