/*
 * Copyright (c) 2026 SES-GO / UFG
 * Todos os direitos reservados.
 */

package br.gov.go.saude.hubsaude.client.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Testes unitários das lógicas puras de {@link VerificarAcesso}:
 * parsing de argumentos de linha de comando (contrato de flags e valores
 * padrão), detecção de flags, validação de arquivos, redação de segredos
 * na saída (área sensível — o token jamais deve vazar no terminal) e
 * rotinas de exibição no terminal (capturando stdout/stderr).
 */
class VerificarAcessoTest {

    private static final String[] OBRIGATORIOS = {
        "--client-id=hs-12345678", "--key=chave.pem", "--cert=cert.pem", "--ig=hemograma", "--ig-versao=0.0.1",
    };

    // ------------------------------------------------------------------
    // parseArgs: parâmetros obrigatórios
    // ------------------------------------------------------------------

    @Test
    void deveExigirClientId() {
        assertThatThrownBy(() -> VerificarAcesso.parseArgs("--key=chave.pem", "--cert=cert.pem"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--client-id");
    }

    @Test
    void deveExigirChavePrivada() {
        assertThatThrownBy(() -> VerificarAcesso.parseArgs("--client-id=hs-1", "--cert=cert.pem"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--key");
    }

    @Test
    void deveExigirCertificado() {
        assertThatThrownBy(() -> VerificarAcesso.parseArgs("--client-id=hs-1", "--key=chave.pem"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--cert");
    }

    @Test
    void deveExigirIg() {
        // Concern client-assertion-contexto-ig.md §3.4: o claim hub_ctx é
        // obrigatório no client_assertion enviado ao HubSaúde.
        assertThatThrownBy(() -> VerificarAcesso.parseArgs(
                        "--client-id=hs-1", "--key=chave.pem", "--cert=cert.pem", "--ig-versao=0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--ig");
    }

    @Test
    void deveExigirIgVersao() {
        assertThatThrownBy(() -> VerificarAcesso.parseArgs(
                        "--client-id=hs-1", "--key=chave.pem", "--cert=cert.pem", "--ig=hemograma"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--ig-versao");
    }

    @Test
    void deveRejeitarIgComFormatoInvalido() {
        assertThatThrownBy(() -> VerificarAcesso.parseArgs(
                        "--client-id=hs-1",
                        "--key=chave.pem",
                        "--cert=cert.pem",
                        "--ig=Hemograma",
                        "--ig-versao=0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--ig inválido");
    }

    @Test
    void deveRejeitarIgVersaoIncompleta() {
        assertThatThrownBy(() -> VerificarAcesso.parseArgs(
                        "--client-id=hs-1", "--key=chave.pem", "--cert=cert.pem", "--ig=hemograma", "--ig-versao=1.2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--ig-versao inválido");
    }

    @Test
    void deveRejeitarIgVersaoComPreRelease() {
        assertThatThrownBy(() -> VerificarAcesso.parseArgs(
                        "--client-id=hs-1",
                        "--key=chave.pem",
                        "--cert=cert.pem",
                        "--ig=hemograma",
                        "--ig-versao=1.2.3-rc.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--ig-versao inválido");
    }

    // ------------------------------------------------------------------
    // parseArgs: valores padrão e parsing de cada flag
    // ------------------------------------------------------------------

    @Test
    void deveAplicarValoresPadrao() {
        final VerificarAcesso.Config config = VerificarAcesso.parseArgs(OBRIGATORIOS);

        assertThat(config.clientId).isEqualTo("hs-12345678");
        assertThat(config.keyPath).isEqualTo("chave.pem");
        assertThat(config.certPath).isEqualTo("cert.pem");
        assertThat(config.ig).isEqualTo("hemograma");
        assertThat(config.igVersao).isEqualTo("0.0.1");
        assertThat(config.tokenEndpoint).isNull();
        assertThat(config.fhirBase).isEqualTo("https://hub.saude.go.gov.br");
        assertThat(config.scope).isEqualTo("system/Patient.rs");
        assertThat(config.tlsProtocol).isEqualTo("TLSv1.3");
        // Concern client-assertion-contexto-ig.md, §3.2: padrão RS384.
        assertThat(config.jwtAlgorithm).isEqualTo("RS384");
        assertThat(config.keyId).isNull();
        assertThat(config.trustAnchorPath).isNull();
        assertThat(config.keyPassword).isNull();
        assertThat(config.quiet).isFalse();
    }

    @Test
    void deveInterpretarTodosOsParametrosOpcionais() {
        final VerificarAcesso.Config config = VerificarAcesso.parseArgs(
                "--client-id=hs-1",
                "--key=k.pem",
                "--cert=c.pem",
                "--ig=hemograma",
                "--ig-versao=1.2.3",
                "--endpoint=https://auth.exemplo.gov.br/token",
                "--fhir-base=https://fhir.exemplo.gov.br",
                "--scope=system/Observation.rs",
                "--password=segredo",
                "--tls=TLSv1.2",
                "--alg=es384",
                "--kid=chave-2026",
                "--trust=anchor.pem",
                "--quiet");

        assertThat(config.tokenEndpoint).isEqualTo("https://auth.exemplo.gov.br/token");
        assertThat(config.fhirBase).isEqualTo("https://fhir.exemplo.gov.br");
        assertThat(config.scope).isEqualTo("system/Observation.rs");
        assertThat(config.keyPassword).isEqualTo("segredo".toCharArray());
        assertThat(config.tlsProtocol).isEqualTo("TLSv1.2");
        assertThat(config.jwtAlgorithm).isEqualTo("ES384");
        assertThat(config.keyId).isEqualTo("chave-2026");
        assertThat(config.ig).isEqualTo("hemograma");
        assertThat(config.igVersao).isEqualTo("1.2.3");
        assertThat(config.trustAnchorPath).isEqualTo("anchor.pem");
        assertThat(config.quiet).isTrue();
    }

    @Test
    void deveNormalizarAlgoritmoParaMaiusculas() {
        final VerificarAcesso.Config config = VerificarAcesso.parseArgs(
                "--client-id=hs-1",
                "--key=k.pem",
                "--cert=c.pem",
                "--ig=hemograma",
                "--ig-versao=0.0.1",
                "--alg=rs384");

        assertThat(config.jwtAlgorithm).isEqualTo("RS384");
    }

    @Test
    void deveReconhecerQuietNaFormaCurta() {
        final VerificarAcesso.Config config = VerificarAcesso.parseArgs(
                "--client-id=hs-1", "--key=k.pem", "--cert=c.pem", "--ig=hemograma", "--ig-versao=0.0.1", "-q");

        assertThat(config.quiet).isTrue();
    }

    @Test
    void deveIgnorarHelpDuranteParsing() {
        final VerificarAcesso.Config config = VerificarAcesso.parseArgs(
                "--help",
                "-h",
                "--client-id=hs-1",
                "--key=k.pem",
                "--cert=c.pem",
                "--ig=hemograma",
                "--ig-versao=0.0.1");

        assertThat(config.clientId).isEqualTo("hs-1");
    }

    @Test
    void deveRejeitarArgumentoDesconhecido() {
        assertThatThrownBy(() -> VerificarAcesso.parseArgs("--client-id=hs-1", "--desconhecido=x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Argumento desconhecido: --desconhecido=x");
    }

    @Test
    void deveRejeitarListaVaziaDeArgumentos() {
        assertThatThrownBy(VerificarAcesso::parseArgs)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--client-id");
    }

    // ------------------------------------------------------------------
    // hasFlag
    // ------------------------------------------------------------------

    @Test
    void deveDetectarFlagPresenteEmQualquerAlias() {
        final String[] args = {"--client-id=hs-1", "-q"};

        assertThat(VerificarAcesso.hasFlag(args, "--quiet", "-q")).isTrue();
        assertThat(VerificarAcesso.hasFlag(args, "--quiet")).isFalse();
        assertThat(VerificarAcesso.hasFlag(new String[0], "--quiet", "-q")).isFalse();
    }

    @Test
    void naoDeveConfundirPrefixoComFlagExata() {
        final String[] args = {"--quiet-mode"};

        assertThat(VerificarAcesso.hasFlag(args, "--quiet")).isFalse();
    }

    // ------------------------------------------------------------------
    // validateFile
    // ------------------------------------------------------------------

    @Test
    void deveAceitarArquivoExistenteLegivel(@TempDir final Path tempDir) throws IOException {
        final Path arquivo = tempDir.resolve("chave.pem");
        Files.writeString(arquivo, "-----BEGIN PRIVATE KEY-----", StandardCharsets.UTF_8);

        assertThatCode(() -> VerificarAcesso.validateFile(arquivo.toString(), "Chave privada"))
                .doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarArquivoInexistente(@TempDir final Path tempDir) {
        final String inexistente = tempDir.resolve("nao-existe.pem").toString();

        assertThatThrownBy(() -> VerificarAcesso.validateFile(inexistente, "Certificado"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Certificado não encontrado");
    }

    // ------------------------------------------------------------------
    // redactSecrets: o segredo NÃO pode aparecer na saída
    // ------------------------------------------------------------------

    @Test
    void deveRedigirAccessTokenSemVazarSegredo() {
        final String segredo = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJocy0xIn0.assinatura-secreta";
        final String json = "{\"access_token\":\"" + segredo + "\",\"token_type\":\"Bearer\",\"expires_in\":300}";

        final String saida = VerificarAcesso.redactSecrets(json);

        assertThat(saida)
                .doesNotContain(segredo)
                .contains("\"access_token\":\"<REDACTED>\"")
                .contains("\"token_type\":\"Bearer\"")
                .contains("\"expires_in\":300");
    }

    @Test
    void deveRedigirRefreshTokenEIdToken() {
        final String json = "{\"refresh_token\":\"refresh-secreto\",\"id_token\":\"id-secreto\"}";

        final String saida = VerificarAcesso.redactSecrets(json);

        assertThat(saida)
                .doesNotContain("refresh-secreto")
                .doesNotContain("id-secreto")
                .contains("\"refresh_token\":\"<REDACTED>\"")
                .contains("\"id_token\":\"<REDACTED>\"");
    }

    @Test
    void deveRedigirSegredosMesmoComEspacosAoRedorDoDoisPontos() {
        final String json = "{\"access_token\" : \"segredo-com-espacos\"}";

        final String saida = VerificarAcesso.redactSecrets(json);

        assertThat(saida).doesNotContain("segredo-com-espacos").contains("<REDACTED>");
    }

    @Test
    void naoDeveExporSegredoNaSaidaTabularFinal() {
        final String segredo = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJocy0xIn0.assinatura-secreta";
        final String json =
                "{\"access_token\":\"" + segredo + "\",\"token_type\":\"Bearer\",\"scope\":" + "\"system/Patient.rs\"}";

        final String saida = JwtPayloadFormatter.formatAsTable(VerificarAcesso.redactSecrets(json), "    ");

        assertThat(saida)
                .doesNotContain(segredo)
                .contains("<REDACTED>")
                .contains("Bearer")
                .contains("system/Patient.rs");
    }

    // ------------------------------------------------------------------
    // promptPasswordIfNeeded (via parseArgs): detecção de chave criptografada
    // ------------------------------------------------------------------

    @Test
    void naoDeveSolicitarSenhaParaChaveNaoCriptografada(@TempDir final Path tempDir) throws IOException {
        final Path chave = tempDir.resolve("chave.pem");
        Files.writeString(
                chave, "-----BEGIN PRIVATE KEY-----\nMIIB\n-----END PRIVATE KEY-----", StandardCharsets.UTF_8);

        final VerificarAcesso.Config config = VerificarAcesso.parseArgs(
                "--client-id=hs-1", "--key=" + chave, "--cert=c.pem", "--ig=hemograma", "--ig-versao=0.0.1");

        assertThat(config.keyPassword).isNull();
    }

    @Test
    void deveDetectarChaveCriptografadaSemConsoleDisponivel(@TempDir final Path tempDir) throws IOException {
        final Path chave = tempDir.resolve("chave-cifrada.pem");
        Files.writeString(
                chave,
                "-----BEGIN ENCRYPTED PRIVATE KEY-----\nMIIB\n-----END ENCRYPTED PRIVATE KEY-----",
                StandardCharsets.UTF_8);

        // Sob Surefire não há console interativo: a detecção ocorre, mas a
        // senha permanece nula (será exigida adiante na abertura da chave).
        final VerificarAcesso.Config config = VerificarAcesso.parseArgs(
                "--client-id=hs-1", "--key=" + chave, "--cert=c.pem", "--ig=hemograma", "--ig-versao=0.0.1");

        assertThat(config.keyPassword).isNull();
    }

    @Test
    void naoDeveConsultarConsoleQuandoSenhaJaFornecida(@TempDir final Path tempDir) throws IOException {
        final Path chave = tempDir.resolve("chave-cifrada.pem");
        Files.writeString(chave, "ENCRYPTED", StandardCharsets.UTF_8);

        final VerificarAcesso.Config config = VerificarAcesso.parseArgs(
                "--client-id=hs-1",
                "--key=" + chave,
                "--cert=c.pem",
                "--ig=hemograma",
                "--ig-versao=0.0.1",
                "--password=abc");

        assertThat(config.keyPassword).isEqualTo("abc".toCharArray());
    }

    // ------------------------------------------------------------------
    // Rotinas de exibição: capturam stdout/stderr e verificam o conteúdo
    // ------------------------------------------------------------------

    @Test
    void deveExibirBannerComNomeDaFerramenta() {
        final String saida = capturarStdout(VerificarAcesso::printBanner);

        assertThat(saida).contains("HubSaúde").contains("Verificador de Acesso");
    }

    @Test
    void deveExibirUsoComParametrosExemplosECodigosDeSaida() {
        final String saida = capturarStdout(VerificarAcesso::printUsage);

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
                .contains("--help, -h")
                .contains("EXEMPLOS:")
                .contains("CÓDIGOS DE SAÍDA:");
    }

    @Test
    void deveExibirMensagensComPrefixosPadronizados() {
        assertThat(capturarStdout(() -> VerificarAcesso.printInfo("informativa")))
                .contains("[i] informativa");
        assertThat(capturarStdout(() -> VerificarAcesso.printSuccess("concluida")))
                .contains("[OK] concluida");
        assertThat(capturarStderr(() -> VerificarAcesso.printError("falhou"))).contains("[X] falhou");
    }

    @Test
    void deveExibirHeaderPayloadETokenCompletoParaJwtValido() {
        final String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        final String payload = base64Url("{\"iss\":\"https://auth.exemplo.gov.br\",\"exp\":1735689600}");
        final String token = header + "." + payload + ".assinatura-fake";

        final String saida = capturarStdout(() -> VerificarAcesso.printTokenDetails(token));

        assertThat(saida)
                .contains("Header:")
                .contains("alg")
                .contains("RS256")
                .contains("Payload:")
                .contains("https://auth.exemplo.gov.br")
                .contains("Token completo:");
    }

    @Test
    void deveQuebrarTokenLongoEmLinhasDeLarguraFixa() {
        final String token = "x".repeat(200);

        final String saida = capturarStdout(() -> VerificarAcesso.printTokenDetails(token));

        assertThat(saida).contains("    " + "x".repeat(80)).contains("    " + "x".repeat(40));
    }

    @Test
    void deveExibirFallbackQuandoTokenNaoDecodificavel() {
        // Duas partes separadas por ponto, mas com Base64URL inválido
        final String saida = capturarStdout(() -> VerificarAcesso.printTokenDetails("!!!.###"));

        assertThat(saida).contains("(Não foi possível decodificar o token)").contains("Token completo:");
    }

    @Test
    void deveExibirRespostaDoKeycloakSemVazarToken() {
        final String segredo = "jwt-fake-que-nao-pode-vazar";
        final String json = "{\"access_token\":\"" + segredo + "\",\"token_type\":\"Bearer\",\"expires_in\":300}";

        final String saida = capturarStdout(() -> VerificarAcesso.printKeycloakResponse(json));

        assertThat(saida)
                .contains("Resposta do servidor de autorização")
                .contains("<REDACTED>")
                .contains("Bearer")
                .doesNotContain(segredo);
    }

    @Test
    void naoDeveExibirNadaParaRespostaNulaOuEmBranco() {
        assertThat(capturarStdout(() -> VerificarAcesso.printKeycloakResponse(null)))
                .isEmpty();
        assertThat(capturarStdout(() -> VerificarAcesso.printKeycloakResponse("   ")))
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // Utilitários de teste
    // ------------------------------------------------------------------

    private static String base64Url(final String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
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
