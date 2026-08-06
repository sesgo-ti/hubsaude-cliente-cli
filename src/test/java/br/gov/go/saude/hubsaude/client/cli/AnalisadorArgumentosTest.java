/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2025-2026 Estado de Goiás (SES-GO) e Universidade Federal de Goiás (UFG).
 */

package br.gov.go.saude.hubsaude.client.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Testes unitários de {@link AnalisadorArgumentos}: parsing de argumentos de
 * linha de comando (contrato de flags e valores padrão), detecção de flags,
 * validação de arquivos e detecção de chave criptografada.
 */
class AnalisadorArgumentosTest {

    private static final String[] OBRIGATORIOS = {
        "--client-id=hs-12345678", "--key=chave.pem", "--cert=cert.pem", "--ig=hemograma", "--ig-versao=0.0.1",
    };

    // ------------------------------------------------------------------
    // parseArgs: parâmetros obrigatórios
    // ------------------------------------------------------------------

    @Test
    void deveExigirClientId() {
        assertThatThrownBy(() -> AnalisadorArgumentos.parseArgs("--key=chave.pem", "--cert=cert.pem"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--client-id");
    }

    @Test
    void deveExigirChavePrivada() {
        assertThatThrownBy(() -> AnalisadorArgumentos.parseArgs("--client-id=hs-1", "--cert=cert.pem"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--key");
    }

    @Test
    void deveExigirCertificado() {
        assertThatThrownBy(() -> AnalisadorArgumentos.parseArgs("--client-id=hs-1", "--key=chave.pem"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--cert");
    }

    @Test
    void deveExigirIg() {
        // Concern client-assertion-contexto-ig.md §3.4: o claim hub_ctx é
        // obrigatório no client_assertion enviado ao HubSaúde.
        assertThatThrownBy(() -> AnalisadorArgumentos.parseArgs(
                        "--client-id=hs-1", "--key=chave.pem", "--cert=cert.pem", "--ig-versao=0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--ig");
    }

    @Test
    void deveExigirIgVersao() {
        assertThatThrownBy(() -> AnalisadorArgumentos.parseArgs(
                        "--client-id=hs-1", "--key=chave.pem", "--cert=cert.pem", "--ig=hemograma"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--ig-versao");
    }

    @Test
    void deveRejeitarIgComFormatoInvalido() {
        assertThatThrownBy(() -> AnalisadorArgumentos.parseArgs(
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
        assertThatThrownBy(() -> AnalisadorArgumentos.parseArgs(
                        "--client-id=hs-1", "--key=chave.pem", "--cert=cert.pem", "--ig=hemograma", "--ig-versao=1.2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--ig-versao inválido");
    }

    @Test
    void deveRejeitarIgVersaoComPreRelease() {
        assertThatThrownBy(() -> AnalisadorArgumentos.parseArgs(
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
        final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(OBRIGATORIOS);

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
        assertThat(config.showToken).isFalse();
    }

    @Test
    void deveInterpretarTodosOsParametrosOpcionais() {
        final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(
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
                "--show-token",
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
        assertThat(config.showToken).isTrue();
    }

    @Test
    void deveNormalizarAlgoritmoParaMaiusculas() {
        final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(
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
        final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(
                "--client-id=hs-1", "--key=k.pem", "--cert=c.pem", "--ig=hemograma", "--ig-versao=0.0.1", "-q");

        assertThat(config.quiet).isTrue();
    }

    @Test
    void deveIgnorarHelpDuranteParsing() {
        final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(
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
        assertThatThrownBy(() -> AnalisadorArgumentos.parseArgs("--client-id=hs-1", "--desconhecido=x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Argumento desconhecido: --desconhecido=x");
    }

    @Test
    void deveRejeitarListaVaziaDeArgumentos() {
        assertThatThrownBy(AnalisadorArgumentos::parseArgs)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--client-id");
    }

    // ------------------------------------------------------------------
    // hasFlag
    // ------------------------------------------------------------------

    @Test
    void deveDetectarFlagPresenteEmQualquerAlias() {
        final String[] args = {"--client-id=hs-1", "-q"};

        assertThat(AnalisadorArgumentos.hasFlag(args, "--quiet", "-q")).isTrue();
        assertThat(AnalisadorArgumentos.hasFlag(args, "--quiet")).isFalse();
        assertThat(AnalisadorArgumentos.hasFlag(new String[0], "--quiet", "-q")).isFalse();
    }

    @Test
    void naoDeveConfundirPrefixoComFlagExata() {
        final String[] args = {"--quiet-mode"};

        assertThat(AnalisadorArgumentos.hasFlag(args, "--quiet")).isFalse();
    }

    // ------------------------------------------------------------------
    // validateFile
    // ------------------------------------------------------------------

    @Test
    void deveAceitarArquivoExistenteLegivel(@TempDir final Path tempDir) throws IOException {
        final Path arquivo = tempDir.resolve("chave.pem");
        Files.writeString(arquivo, "-----BEGIN PRIVATE KEY-----", StandardCharsets.UTF_8);

        assertThatCode(() -> AnalisadorArgumentos.validateFile(arquivo.toString(), "Chave privada"))
                .doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarArquivoInexistente(@TempDir final Path tempDir) {
        final String inexistente = tempDir.resolve("nao-existe.pem").toString();

        assertThatThrownBy(() -> AnalisadorArgumentos.validateFile(inexistente, "Certificado"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Certificado não encontrado");
    }

    // ------------------------------------------------------------------
    // promptPasswordIfNeeded (via parseArgs): detecção de chave criptografada
    // ------------------------------------------------------------------

    @Test
    void naoDeveSolicitarSenhaParaChaveNaoCriptografada(@TempDir final Path tempDir) throws IOException {
        final Path chave = tempDir.resolve("chave.pem");
        Files.writeString(
                chave, "-----BEGIN PRIVATE KEY-----\nMIIB\n-----END PRIVATE KEY-----", StandardCharsets.UTF_8);

        final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(
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
        final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(
                "--client-id=hs-1", "--key=" + chave, "--cert=c.pem", "--ig=hemograma", "--ig-versao=0.0.1");

        assertThat(config.keyPassword).isNull();
    }

    @Test
    void naoDeveConsultarConsoleQuandoSenhaJaFornecida(@TempDir final Path tempDir) throws IOException {
        final Path chave = tempDir.resolve("chave-cifrada.pem");
        Files.writeString(chave, "ENCRYPTED", StandardCharsets.UTF_8);

        final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(
                "--client-id=hs-1",
                "--key=" + chave,
                "--cert=c.pem",
                "--ig=hemograma",
                "--ig-versao=0.0.1",
                "--password=abc");

        assertThat(config.keyPassword).isEqualTo("abc".toCharArray());
    }
}
