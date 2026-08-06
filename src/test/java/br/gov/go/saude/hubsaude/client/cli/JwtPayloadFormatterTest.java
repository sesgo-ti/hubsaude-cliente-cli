/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2025-2026 Estado de Goiás (SES-GO) e Universidade Federal de Goiás (UFG).
 */

package br.gov.go.saude.hubsaude.client.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * Testes unitários de {@link JwtPayloadFormatter}: formatação tabular de
 * Header/Payload JWT (objeto JSON plano), preservação da ordem das claims,
 * interpretação de escapes JSON e fallback seguro para entradas inválidas.
 */
class JwtPayloadFormatterTest {

    private static final String INDENT = "    ";

    @Test
    void deveFormatarClaimsTipicasDeJwtComoTabelaAlinhada() {
        final String payload = "{\"iss\":\"https://auth.saude.go.gov.br/realms/hubsaude\","
                + "\"sub\":\"hs-12345678\",\"aud\":\"https://hub.saude.go.gov.br\","
                + "\"exp\":1751998800,\"scope\":\"system/Patient.rs\"}";

        final String saida = JwtPayloadFormatter.formatAsTable(payload, INDENT);

        assertThat(saida.lines())
                .containsExactly(
                        "    iss   : https://auth.saude.go.gov.br/realms/hubsaude",
                        "    sub   : hs-12345678",
                        "    aud   : https://hub.saude.go.gov.br",
                        "    exp   : 1751998800",
                        "    scope : system/Patient.rs");
    }

    @Test
    void deveFormatarHeaderJwtValido() {
        final String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"chave-1\"}";

        final String saida = JwtPayloadFormatter.formatAsTable(header, INDENT);

        assertThat(saida.lines()).containsExactly("    alg : RS256", "    typ : JWT", "    kid : chave-1");
    }

    @Test
    void devePreservarOrdemDeInsercaoDasClaims() {
        final String json = "{\"zebra\":1,\"alfa\":2,\"meio\":3}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida.indexOf("zebra")).isLessThan(saida.indexOf("alfa")).isLessThan(saida.indexOf("meio"));
    }

    @Test
    void deveManterValoresAninhadosComoJsonCompacto() {
        final String json = "{\"cnf\":{\"jkt\":\"abc\"},\"amr\":[\"mtls\",\"jwt\"]}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida.lines()).containsExactly("    cnf : {\"jkt\":\"abc\"}", "    amr : [\"mtls\",\"jwt\"]");
    }

    @Test
    void deveManterNumerosBooleanosENullComoEscalares() {
        final String json = "{\"exp\":123,\"ativo\":true,\"nbf\":null}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida.lines()).containsExactly("    exp   : 123", "    ativo : true", "    nbf   : null");
    }

    @Test
    void deveTolerarEspacosEntreChavesEValores() {
        final String json = "{ \"a\" : 1 , \"b\" : \"x\" }";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida.lines()).containsExactly("    a : 1", "    b : x");
    }

    @Test
    void deveInterpretarEscapesUnicodeEAspas() {
        final String json = "{\"txt\":\"cita\\u00e7\\u00e3o \\\"x\\\"\"}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).contains("cita\u00e7\u00e3o \"x\"");
    }

    @Test
    void deveInterpretarEscapesDeControle() {
        final String json = "{\"txt\":\"l1\\nl2\\tfim\"}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).contains("l1\nl2\tfim");
    }

    @Test
    void deveDevolverTextoNaoJsonEmLinhaUnicaIndentada() {
        final String texto = "isto não é um JSON";

        final String saida = JwtPayloadFormatter.formatAsTable(texto, INDENT);

        assertThat(saida).isEqualTo(INDENT + texto);
    }

    @Test
    void deveDevolverArrayJsonEmLinhaUnicaIndentada() {
        final String json = "[1,2,3]";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).isEqualTo(INDENT + json);
    }

    @Test
    void deveDevolverObjetoMalformadoSemDoisPontosComoFallback() {
        final String json = "{\"a\"}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).isEqualTo(INDENT + json);
    }

    @Test
    void deveDevolverStringNaoTerminadaComoFallback() {
        final String json = "{\"a\":\"b}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).isEqualTo(INDENT + json);
    }

    @Test
    void deveDevolverObjetoVazioComoFallback() {
        final String json = "{}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).isEqualTo(INDENT + json);
    }

    @Test
    void naoDeveLancarExcecaoParaEntradaNula() {
        assertThatCode(() -> JwtPayloadFormatter.formatAsTable(null, INDENT)).doesNotThrowAnyException();
    }

    @Test
    void deveInterpretarTodosOsEscapesSimples() {
        // Escapes JSON válidos: \\ \/ \r \b \f
        final String json = "{\"txt\":\"p\\\\q \\/ r\\rs\\bt\\fu\"}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).contains("p\\q / r\rs\bt\fu");
    }

    @Test
    void deveDevolverEscapeDesconhecidoComoFallback() {
        // Regressão #1819: \z é inválido em JSON; o parser leniente
        // anterior exibia a claim deturpada ("zv"). O parser estrito
        // rejeita e degrada para a linha única (payload bruto).
        final String json = "{\"txt\":\"a\\zv\"}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).isEqualTo(INDENT + json);
    }

    @Test
    void deveDevolverObjetoComVirgulaPendenteComoFallback() {
        // Vírgula pendente é JSON inválido (RFC 8259); o parser estrito
        // rejeita e devolve o texto bruto em linha única.
        final String json = "{\"a\":1,   }";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).isEqualTo(INDENT + json);
    }

    @Test
    void deveDevolverEstruturaAninhadaNaoTerminadaComoFallback() {
        // O array aninhado nunca fecha antes do fim do texto
        final String json = "{\"a\":[1,2}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).isEqualTo(INDENT + json);
    }

    // ------------------------------------------------------------------
    // Escape \\uXXXX com hexadecimal malformado (entrada externa) — #1407
    // ------------------------------------------------------------------

    @Test
    void naoDeveLancarExcecaoParaEscapeUnicodeComHexMalformado() {
        // Payload JWT de origem externa com \\uZZZZ: jamais NumberFormatException
        final String json = "{\"txt\":\"a\\uZZZZb\"}";

        assertThatCode(() -> JwtPayloadFormatter.formatAsTable(json, INDENT)).doesNotThrowAnyException();
    }

    @Test
    void deveDevolverEscapeUnicodeMalformadoComoFallback() {
        // Regressão #1819: o parser leniente anterior exibia a claim
        // deturpada ("auGGGGb"). O parser estrito rejeita o escape
        // inválido e devolve o payload bruto em linha única, sem
        // mascarar a corrupção.
        final String json = "{\"txt\":\"a\\uGGGGb\",\"sub\":\"hs-1\"}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).isEqualTo(INDENT + json);
    }

    @Test
    void deveDevolverEscapeUnicodeComHexParcialmenteValidoComoFallback() {
        // Três dígitos válidos e um inválido ("00GA"): sem conversão
        // parcial — a entrada malformada cai no fallback bruto.
        final String json = "{\"txt\":\"x\\u00GAy\"}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).isEqualTo(INDENT + json);
    }

    @Test
    void deveManterEscapeUnicodeValidoAposCorrecaoDoMalformado() {
        // Happy path preservado: \\u00e7 continua interpretado como "ç"
        final String json = "{\"txt\":\"cita\\u00e7\\u00e3o\"}";

        final String saida = JwtPayloadFormatter.formatAsTable(json, INDENT);

        assertThat(saida).contains("cita\u00e7\u00e3o");
    }
}
