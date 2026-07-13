/*
 * Copyright (c) 2026 SES-GO / UFG
 * Todos os direitos reservados.
 */

package br.gov.go.saude.hubsaude.client.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.gov.go.saude.hubsaude.client.SmartTokenClient;

/**
 * Testes unitários do fluxo de orquestração de {@link VerificarAcesso}:
 * validação dos arquivos configurados, criação do cliente pela
 * {@link ClienteToken.Fabrica} e exibição do resultado — usando uma fábrica
 * fake no lugar do {@code SmartTokenClient} real (sem rede).
 */
class VerificarAcessoTest {

    private static final String TOKEN_FAKE = "eyJhbGciOiJSUzM4NCJ9.eyJzdWIiOiJocy0xIn0.assinatura-fake";

    // ------------------------------------------------------------------
    // verificarAcesso: happy path com fábrica fake
    // ------------------------------------------------------------------

    @Test
    void deveExibirConfiguracaoEResultadoDetalhadoPorPadrao(@TempDir final Path tempDir) throws Exception {
        final AnalisadorArgumentos.Config config = configComArquivos(tempDir);
        final ClienteToken cliente = new ClienteToken(
                "RS384",
                "https://auth.exemplo.gov.br/token",
                scope -> new SmartTokenClient.TokenResponse(
                        TOKEN_FAKE, 300, "{\"access_token\":\"" + TOKEN_FAKE + "\",\"expires_in\":300}"));

        final String saida = capturarStdout(() -> VerificarAcesso.verificarAcesso(config, cfg -> cliente));

        assertThat(saida)
                .contains("Configuração:")
                .contains("Client ID:  hs-1")
                .contains("Algoritmo:  RS384")
                .contains("FHIR Base:  https://hub.saude.go.gov.br")
                .contains("Endpoint:   https://auth.exemplo.gov.br/token (descoberto via .well-known)")
                .contains("Obtendo token de acesso...")
                .contains("Token obtido com sucesso!")
                .contains("<REDACTED>")
                .contains("Detalhes do token JWT:")
                .contains("Credenciamento verificado. Acesso ao HubSaúde está funcionando.")
                .doesNotContain(TOKEN_FAKE + "\"");
    }

    @Test
    void deveOcultarDetalhesDoTokenEmModoQuiet(@TempDir final Path tempDir) throws Exception {
        final AnalisadorArgumentos.Config config =
                configComArquivos(tempDir, "--endpoint=https://auth.exemplo.gov.br/token", "--quiet");
        final ClienteToken cliente = new ClienteToken(
                "RS384",
                "https://auth.exemplo.gov.br/token",
                scope -> new SmartTokenClient.TokenResponse(TOKEN_FAKE, 300, "{}"));

        final String saida = capturarStdout(() -> VerificarAcesso.verificarAcesso(config, cfg -> cliente));

        assertThat(saida)
                .contains("Endpoint:   https://auth.exemplo.gov.br/token")
                .doesNotContain("(descoberto via .well-known)")
                .contains("Token (primeiros 50 chars): " + TOKEN_FAKE.substring(0, 50) + "...")
                .contains("Omita --quiet para ver detalhes do token.")
                .doesNotContain("Detalhes do token JWT:");
    }

    @Test
    void deveRepassarOScopeConfiguradoAoCliente(@TempDir final Path tempDir) throws Exception {
        final AnalisadorArgumentos.Config config = configComArquivos(tempDir, "--scope=system/Observation.rs");
        final AtomicReference<String> scopeRecebido = new AtomicReference<>();
        final ClienteToken cliente = new ClienteToken("RS384", "https://auth.exemplo.gov.br/token", scope -> {
            scopeRecebido.set(scope);
            return new SmartTokenClient.TokenResponse(TOKEN_FAKE, 300, "{}");
        });

        capturarStdout(() -> VerificarAcesso.verificarAcesso(config, cfg -> cliente));

        assertThat(scopeRecebido.get()).isEqualTo("system/Observation.rs");
    }

    // ------------------------------------------------------------------
    // verificarAcesso: validação dos arquivos antes de criar o cliente
    // ------------------------------------------------------------------

    @Test
    void deveRejeitarChavePrivadaInexistenteAntesDeCriarOCliente(@TempDir final Path tempDir) {
        final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(
                "--client-id=hs-1",
                "--key=" + tempDir.resolve("nao-existe.pem"),
                "--cert=" + tempDir.resolve("tambem-nao.pem"),
                "--ig=hemograma",
                "--ig-versao=0.0.1");

        assertThatThrownBy(() -> VerificarAcesso.verificarAcesso(config, cfg -> {
                    throw new AssertionError("fábrica não deveria ser chamada");
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chave privada não encontrado");
    }

    @Test
    void devePropagarFalhaDaFabricaSemCapturar(@TempDir final Path tempDir) throws IOException {
        final AnalisadorArgumentos.Config config = configComArquivos(tempDir);

        assertThatThrownBy(() -> VerificarAcesso.verificarAcesso(config, cfg -> {
                    throw new IOException("descoberta falhou");
                }))
                .isInstanceOf(IOException.class)
                .hasMessage("descoberta falhou");
    }

    // ------------------------------------------------------------------
    // Utilitários de teste
    // ------------------------------------------------------------------

    private static AnalisadorArgumentos.Config configComArquivos(final Path tempDir, final String... extras)
            throws IOException {
        final Path chave = tempDir.resolve("chave.pem");
        final Path cert = tempDir.resolve("cert.pem");
        Files.writeString(chave, "-----BEGIN PRIVATE KEY-----", StandardCharsets.UTF_8);
        Files.writeString(cert, "-----BEGIN CERTIFICATE-----", StandardCharsets.UTF_8);

        final String[] base = {
            "--client-id=hs-1", "--key=" + chave, "--cert=" + cert, "--ig=hemograma", "--ig-versao=0.0.1",
        };
        final String[] args = new String[base.length + extras.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(extras, 0, args, base.length, extras.length);
        return AnalisadorArgumentos.parseArgs(args);
    }

    @FunctionalInterface
    private interface AcaoComExcecao {
        void executar() throws Exception;
    }

    private static String capturarStdout(final AcaoComExcecao acao) throws Exception {
        final PrintStream original = System.out;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream substituto = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(substituto);
            acao.executar();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
