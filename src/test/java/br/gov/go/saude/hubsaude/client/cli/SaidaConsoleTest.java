/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2025-2026 Estado de Goiás (SES-GO) e Universidade Federal de Goiás (UFG).
 */

package br.gov.go.saude.hubsaude.client.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import br.gov.go.saude.hubsaude.client.SmartTokenClient;

/**
 * Testes unitários de {@link SaidaConsole}: redação de segredos na saída
 * (área sensível — o token jamais deve vazar no terminal), rotinas de
 * exibição no terminal (capturando stdout/stderr) e contrato de erro
 * (mensagem curta e acionável no stderr por padrão, stack trace apenas em
 * modo diagnóstico {@code --debug}).
 */
class SaidaConsoleTest {

    /**
     * Assinatura (3ª parte) do JWT sintético: jamais pode aparecer no
     * stdout sem {@code --show-token}.
     */
    private static final String ASSINATURA_FAKE = "assinatura-fake";

    // ------------------------------------------------------------------
    // redactSecrets: o segredo NÃO pode aparecer na saída
    // ------------------------------------------------------------------

    @Test
    void deveRedigirAccessTokenSemVazarSegredo() {
        final String segredo = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJocy0xIn0.assinatura-secreta";
        final String json = "{\"access_token\":\"" + segredo + "\",\"token_type\":\"Bearer\",\"expires_in\":300}";

        final String saida = SaidaConsole.redactSecrets(json);

        assertThat(saida)
                .doesNotContain(segredo)
                .contains("\"access_token\":\"<REDACTED>\"")
                .contains("\"token_type\":\"Bearer\"")
                .contains("\"expires_in\":300");
    }

    @Test
    void deveRedigirRefreshTokenEIdToken() {
        final String json = "{\"refresh_token\":\"refresh-secreto\",\"id_token\":\"id-secreto\"}";

        final String saida = SaidaConsole.redactSecrets(json);

        assertThat(saida)
                .doesNotContain("refresh-secreto")
                .doesNotContain("id-secreto")
                .contains("\"refresh_token\":\"<REDACTED>\"")
                .contains("\"id_token\":\"<REDACTED>\"");
    }

    @Test
    void deveRedigirSegredosMesmoComEspacosAoRedorDoDoisPontos() {
        final String json = "{\"access_token\" : \"segredo-com-espacos\"}";

        final String saida = SaidaConsole.redactSecrets(json);

        assertThat(saida).doesNotContain("segredo-com-espacos").contains("<REDACTED>");
    }

    @Test
    void naoDeveExporSegredoNaSaidaTabularFinal() {
        final String segredo = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJocy0xIn0.assinatura-secreta";
        final String json =
                "{\"access_token\":\"" + segredo + "\",\"token_type\":\"Bearer\",\"scope\":" + "\"system/Patient.rs\"}";

        final String saida = JwtPayloadFormatter.formatAsTable(SaidaConsole.redactSecrets(json), "    ");

        assertThat(saida)
                .doesNotContain(segredo)
                .contains("<REDACTED>")
                .contains("Bearer")
                .contains("system/Patient.rs");
    }

    // ------------------------------------------------------------------
    // Rotinas de exibição: capturam stdout/stderr e verificam o conteúdo
    // ------------------------------------------------------------------

    @Test
    void deveExibirBannerComNomeDaFerramenta() {
        final String saida = capturarStdout(SaidaConsole::printBanner);

        assertThat(saida).contains("HubSaúde").contains("Verificador de Acesso");
    }

    @Test
    void deveExibirUsoComParametrosExemplosECodigosDeSaida() {
        final String saida = capturarStdout(SaidaConsole::printUsage);

        assertThat(saida)
                .contains("USO:")
                .contains("--client-id=<ID>")
                .contains("--key=<PATH>")
                .contains("--cert=<PATH>")
                .contains("--ig=<ALIAS>")
                .contains("--ig-versao=<SEMVER>")
                .contains("--endpoint=<URL>")
                .contains("--fhir-base=<URL>")
                .contains("--scope=<SCOPE>")
                .contains("--tls=<VERSAO>")
                .contains("--alg=<ALG>")
                .contains("--kid=<KID>")
                .contains("--trust=<PATH>")
                .contains("--quiet, -q")
                .contains("--show-token")
                .contains("--help, -h")
                .contains("EXEMPLOS:")
                .contains("CÓDIGOS DE SAÍDA:")
                .contains("0  Sucesso")
                .contains("3  Erro de uso")
                .contains("4  Infraestrutura de rede")
                .contains("6  Credencial NÃO apta");
    }

    @Test
    void deveExibirMensagensComPrefixosPadronizados() {
        assertThat(capturarStdout(() -> SaidaConsole.printInfo("informativa"))).contains("[i] informativa");
        assertThat(capturarStdout(() -> SaidaConsole.printSuccess("concluida"))).contains("[OK] concluida");
        assertThat(capturarStderr(() -> SaidaConsole.printError("falhou"))).contains("[X] falhou");
    }

    @Test
    void deveExibirHeaderPayloadEPreviaSemVazarAssinaturaPorPadrao() {
        final String token = jwtValido();

        final String saida = capturarStdout(() -> SaidaConsole.printTokenDetails(token, false));

        assertThat(saida)
                .contains("Header:")
                .contains("alg")
                .contains("RS256")
                .contains("Payload:")
                .contains("https://auth.exemplo.gov.br")
                .contains("Token (primeiros 50 chars): " + token.substring(0, 50) + "...")
                .contains("Use --show-token para exibir o token completo.")
                .doesNotContain("Token completo:")
                .doesNotContain(ASSINATURA_FAKE);
    }

    @Test
    void deveExibirTokenCompletoApenasComShowToken() {
        final String token = jwtValido();

        final String saida = capturarStdout(() -> SaidaConsole.printTokenDetails(token, true));

        assertThat(saida)
                .contains("Header:")
                .contains("Payload:")
                .contains("Token completo:")
                .contains(ASSINATURA_FAKE)
                .doesNotContain("Use --show-token para exibir o token completo.");
    }

    @Test
    void deveQuebrarTokenLongoEmLinhasDeLarguraFixa() {
        final String token = "x".repeat(200);

        final String saida = capturarStdout(() -> SaidaConsole.printTokenDetails(token, true));

        assertThat(saida).contains("    " + "x".repeat(80)).contains("    " + "x".repeat(40));
    }

    @Test
    void deveExibirFallbackQuandoTokenNaoDecodificavel() {
        // Duas partes separadas por ponto, mas com Base64URL inválido
        final String saida = capturarStdout(() -> SaidaConsole.printTokenDetails("!!!.###", true));

        assertThat(saida).contains("(Não foi possível decodificar o token)").contains("Token completo:");
    }

    @Test
    void deveExibirRespostaDoKeycloakSemVazarToken() {
        final String segredo = "jwt-fake-que-nao-pode-vazar";
        final String json = "{\"access_token\":\"" + segredo + "\",\"token_type\":\"Bearer\",\"expires_in\":300}";

        final String saida = capturarStdout(() -> SaidaConsole.printKeycloakResponse(json));

        assertThat(saida)
                .contains("Resposta do servidor de autorização")
                .contains("<REDACTED>")
                .contains("Bearer")
                .doesNotContain(segredo);
    }

    @Test
    void naoDeveExibirNadaParaRespostaNulaOuEmBranco() {
        assertThat(capturarStdout(() -> SaidaConsole.printKeycloakResponse(null)))
                .isEmpty();
        assertThat(capturarStdout(() -> SaidaConsole.printKeycloakResponse("   ")))
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // printConfiguracao / printResultado: blocos exibidos pelo fluxo da CLI
    // ------------------------------------------------------------------

    @Test
    void deveExibirConfiguracaoComEndpointDescoberto() {
        final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(
                "--client-id=hs-1", "--key=k.pem", "--cert=c.pem", "--ig=hemograma", "--ig-versao=0.0.1");

        final String saida = capturarStdout(
                () -> SaidaConsole.printConfiguracao(config, "RS384", "https://auth.exemplo.gov.br/token"));

        assertThat(saida)
                .contains("Configuração:")
                .contains("Client ID:  hs-1")
                .contains("Chave:      k.pem")
                .contains("Certificado:c.pem")
                .contains("Scope:      system/Patient.rs")
                .contains("IG:         hemograma (versão 0.0.1)")
                .contains("TLS:        TLSv1.3")
                .contains("Algoritmo:  RS384")
                .contains("FHIR Base:  https://hub.saude.go.gov.br")
                .contains("Endpoint:   https://auth.exemplo.gov.br/token (descoberto via .well-known)");
    }

    @Test
    void deveExibirConfiguracaoComEndpointExplicito() {
        final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(
                "--client-id=hs-1",
                "--key=k.pem",
                "--cert=c.pem",
                "--ig=hemograma",
                "--ig-versao=0.0.1",
                "--endpoint=https://auth.exemplo.gov.br/token");

        final String saida = capturarStdout(
                () -> SaidaConsole.printConfiguracao(config, "RS384", "https://auth.exemplo.gov.br/token"));

        assertThat(saida)
                .contains("Endpoint:   https://auth.exemplo.gov.br/token")
                .doesNotContain("FHIR Base:")
                .doesNotContain("(descoberto via .well-known)");
    }

    @Test
    void deveExibirResultadoDetalhadoPorPadraoSemVazarAssinatura() {
        final String token = jwtValido();
        final SmartTokenClient.TokenResponse resposta = new SmartTokenClient.TokenResponse(
                token, 300, "{\"access_token\":\"" + token + "\",\"expires_in\":300}");

        final String saida =
                capturarStdout(() -> SaidaConsole.printResultado(resposta, Duration.ofMillis(42), false, false));

        assertThat(saida)
                .contains("Token obtido com sucesso!")
                .contains("Resposta do servidor de autorização")
                .contains("<REDACTED>")
                .contains("Tempo de resposta: 42ms")
                .contains("Detalhes do token JWT:")
                .contains("Token (primeiros 50 chars): " + token.substring(0, 50) + "...")
                .contains("Credenciamento verificado. Acesso ao HubSaúde está funcionando.")
                .doesNotContain("Token completo:")
                .doesNotContain(ASSINATURA_FAKE);
    }

    @Test
    void deveExibirTokenCompletoNoResultadoComShowToken() {
        final String token = jwtValido();
        final SmartTokenClient.TokenResponse resposta = new SmartTokenClient.TokenResponse(
                token, 300, "{\"access_token\":\"" + token + "\",\"expires_in\":300}");

        final String saida =
                capturarStdout(() -> SaidaConsole.printResultado(resposta, Duration.ofMillis(42), false, true));

        assertThat(saida).contains("Token completo:").contains(ASSINATURA_FAKE);
    }

    @Test
    void deveExibirApenasPreviaEmModoQuiet() {
        final String token = "a".repeat(120);
        final SmartTokenClient.TokenResponse resposta = new SmartTokenClient.TokenResponse(token, 300, "{}");

        final String saida =
                capturarStdout(() -> SaidaConsole.printResultado(resposta, Duration.ofMillis(7), true, false));

        assertThat(saida)
                .contains("Token obtido com sucesso!")
                .contains("Tempo de resposta: 7ms")
                .contains("Token (primeiros 50 chars): " + "a".repeat(50) + "...")
                .contains("Omita --quiet para ver detalhes do token.")
                .doesNotContain("Detalhes do token JWT:")
                .doesNotContain("Resposta do servidor de autorização");
    }

    @Test
    void quietPrevaleceSobreShowToken() {
        final String token = "a".repeat(120);
        final SmartTokenClient.TokenResponse resposta = new SmartTokenClient.TokenResponse(token, 300, "{}");

        final String saida =
                capturarStdout(() -> SaidaConsole.printResultado(resposta, Duration.ofMillis(7), true, true));

        assertThat(saida)
                .contains("Token (primeiros 50 chars): " + "a".repeat(50) + "...")
                .doesNotContain("Token completo:");
    }

    // ------------------------------------------------------------------
    // reportarFalha: contrato de erro no stderr
    // ------------------------------------------------------------------

    @Test
    void semDebugEmiteMensagemCurtaSemStackTrace() {
        final String saida = capturarStderr(() -> SaidaConsole.reportarFalha(
                new RuntimeException("endpoint inacessível"), ClassificadorFalhas.INFRA, false));

        assertThat(saida)
                .contains("Falha ao obter token: endpoint inacessível")
                .contains("--debug")
                .doesNotContain("\tat ")
                .doesNotContain("java.lang.RuntimeException");
    }

    @Test
    void comDebugEmiteDetalhesTecnicos() {
        final String saida = capturarStderr(() -> SaidaConsole.reportarFalha(
                new RuntimeException("endpoint inacessível"), ClassificadorFalhas.INFRA, true));

        assertThat(saida)
                .contains("Falha ao obter token: endpoint inacessível")
                .contains("java.lang.RuntimeException")
                .contains("\tat ");
    }

    @Test
    void comDebugEmiteCadeiaDeCausas() {
        final Exception falha = new IllegalStateException("erro externo", new IllegalArgumentException("causa raiz"));

        final String saida = capturarStderr(() -> SaidaConsole.reportarFalha(falha, ClassificadorFalhas.USO, true));

        assertThat(saida).contains("erro externo").contains("Causado por: ").contains("causa raiz");
    }

    // ------------------------------------------------------------------
    // printVeredito: veredito da classificação por código de saída.
    // ------------------------------------------------------------------

    @Test
    void vereditoDeCredencialNaoAptaOrientaOCredenciamento() {
        final String saida = capturarStderr(() -> SaidaConsole.printVeredito(ClassificadorFalhas.CREDENCIAL_NAO_APTA));

        assertThat(saida).contains("Credencial NÃO apta").contains("exit 6");
    }

    @Test
    void vereditoDeInfraOrientaReexecucao() {
        final String saida = capturarStderr(() -> SaidaConsole.printVeredito(ClassificadorFalhas.INFRA));

        assertThat(saida).contains("INCONCLUSIVO").contains("repita mais tarde").contains("exit 4");
    }

    @Test
    void vereditoDeUsoOrientaCorrecaoDosArgumentos() {
        final String saida = capturarStderr(() -> SaidaConsole.printVeredito(ClassificadorFalhas.USO));

        assertThat(saida).contains("Erro de uso").contains("exit 3");
    }

    @Test
    void sucessoNaoEmiteVeredito() {
        assertThat(capturarStderr(() -> SaidaConsole.printVeredito(ClassificadorFalhas.SUCESSO)))
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // Utilitários de teste
    // ------------------------------------------------------------------

    private static String base64Url(final String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Monta um JWT compacto sintético com header e payload decodificáveis e
     * a assinatura identificável {@link #ASSINATURA_FAKE}; o trecho
     * header+payload excede os 50 caracteres da prévia, garantindo que a
     * assinatura jamais entre na prévia truncada.
     */
    private static String jwtValido() {
        final String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        final String payload = base64Url("{\"iss\":\"https://auth.exemplo.gov.br\",\"exp\":1735689600}");
        return header + "." + payload + "." + ASSINATURA_FAKE;
    }

    private static String capturarStdout(final Runnable acao) {
        final PrintStream original = System.out;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream substituto = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(substituto);
            acao.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static String capturarStderr(final Runnable acao) {
        final PrintStream original = System.err;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream substituto = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setErr(substituto);
            acao.run();
        } finally {
            System.setErr(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
