package com.io.appioweb.adapters.integrations.webmotors.soap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebmotorsSoapResponseParserTest {

    private final WebmotorsSoapResponseParser parser = new WebmotorsSoapResponseParser();

    @Test
    void parseAuthExtractsHashAndReturnCode() {
        String xml = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:wm="http://webmotors.com.br/">
                  <soap:Body>
                    <wm:AutenticarResponse>
                      <wm:AutenticarResult>
                        <wm:HashAutenticacao>hash-123</wm:HashAutenticacao>
                        <wm:CodigoRetorno>0</wm:CodigoRetorno>
                        <wm:RequestId>req-001</wm:RequestId>
                      </wm:AutenticarResult>
                    </wm:AutenticarResponse>
                  </soap:Body>
                </soap:Envelope>
                """;

        var result = parser.parseAuth(xml);

        assertThat(result.hashAutenticacao()).isEqualTo("hash-123");
        assertThat(result.codigoRetorno()).isEqualTo("0");
        assertThat(result.requestId()).isEqualTo("req-001");
    }

    @Test
    void parseInventoryPageExtractsItemsAndPaging() {
        String xml = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:wm="http://webmotors.com.br/">
                  <soap:Body>
                    <wm:ObterEstoqueAtualPaginadoResponse>
                      <wm:Pagina>2</wm:Pagina>
                      <wm:AnunciosPorPagina>1</wm:AnunciosPorPagina>
                      <wm:TotalAnuncios>3</wm:TotalAnuncios>
                      <wm:CodigoRetorno>00</wm:CodigoRetorno>
                      <wm:RequestId>req-estoque</wm:RequestId>
                      <wm:Anuncio>
                        <wm:CodigoAnuncio>AD-9</wm:CodigoAnuncio>
                        <wm:CodigoMarca>10</wm:CodigoMarca>
                        <wm:CodigoModelo>20</wm:CodigoModelo>
                        <wm:CodigoVersao>30</wm:CodigoVersao>
                        <wm:Titulo>Honda City Touring</wm:Titulo>
                        <wm:PrecoVenda>123450</wm:PrecoVenda>
                        <wm:Quilometragem>45678</wm:Quilometragem>
                        <wm:Status>PUBLISHED</wm:Status>
                        <wm:DataInclusao>2026-04-10T12:00:00</wm:DataInclusao>
                        <wm:DataUltimaAlteracao>2026-04-11T12:00:00</wm:DataUltimaAlteracao>
                      </wm:Anuncio>
                    </wm:ObterEstoqueAtualPaginadoResponse>
                  </soap:Body>
                </soap:Envelope>
                """;

        var page = parser.parseInventoryPage(xml);

        assertThat(page.pagina()).isEqualTo(2);
        assertThat(page.anunciosPorPagina()).isEqualTo(1);
        assertThat(page.totalAnuncios()).isEqualTo(3);
        assertThat(page.codigoRetorno()).isEqualTo("00");
        assertThat(page.requestId()).isEqualTo("req-estoque");
        assertThat(page.anuncios()).hasSize(1);
        assertThat(page.anuncios().getFirst().codigoAnuncio()).isEqualTo("AD-9");
        assertThat(page.anuncios().getFirst().precoVenda()).isEqualTo(123450L);
        assertThat(page.anuncios().getFirst().quilometragem()).isEqualTo(45678);
    }
}
