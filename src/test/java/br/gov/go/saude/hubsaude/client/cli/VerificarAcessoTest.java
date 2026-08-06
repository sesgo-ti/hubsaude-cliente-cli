/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2025-2026 Estado de Goiás (SES-GO) e Universidade Federal de Goiás (UFG).
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
import br.gov.go.saude.hubsaude.client.SmartTokenException;

/**
 * Testes unitários do fluxo de orquestração de {@link VerificarAcesso}:
 * validação dos arquivos configurados, criação do cliente pela
 * {@link ClienteToken.Fabrica}, exibição do resultado e o contrato de
 * códigos de saída 0/3/4/6 da issue #817 — usando uma fábrica fake no
 * lugar do {@code SmartTokenClient} real (sem rede).
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
                .contains("Token (primeiros 50 chars): " + TOKEN_FAKE.substring(0, 50) + "...")
                .contains("Credenciamento verificado. Acesso ao HubSaúde está funcionando.")
                .doesNotContain("Token completo:")
                .doesNotContain(TOKEN_FAKE);
    }

    @Test
    void deveExibirTokenCompletoComShowToken(@TempDir final Path tempDir) throws Exception {
        final AnalisadorArgumentos.Config config = configComArquivos(tempDir, "--show-token");
        final ClienteToken cliente = new ClienteToken(
                "RS384",
                "https://auth.exemplo.gov.br/token",
                scope -> new SmartTokenClient.TokenResponse(TOKEN_FAKE, 300, "{}"));

        final String saida = capturarStdout(() -> VerificarAcesso.verificarAcesso(config, cfg -> cliente));

        assertThat(saida).contains("Token completo:").contains(TOKEN_FAKE);
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
    // executar: contrato de códigos de saída 0/3/4/6 (issue #817)
    // ------------------------------------------------------------------

    @Test
    void executarComSucessoRetornaZero(@TempDir final Path tempDir) throws Exception {
        final String[] args = argsComArquivos(tempDir);
        final ClienteToken cliente = new ClienteToken(
                "RS384",
                "https://auth.exemplo.gov.br/token",
                scope -> new SmartTokenClient.TokenResponse(TOKEN_FAKE, 300, "{}"));

        final Execucao execucao = executarCapturando(args, cfg -> cliente);

        assertThat(execucao.codigo()).isZero();
        assertThat(execucao.stdout()).contains("Token obtido com sucesso!");
    }

    @Test
    void executarSemArgumentosImprimeUsoERetornaZero() throws Exception {
        final Execucao execucao = executarCapturando(new String[0], cfg -> {
            throw new AssertionError("fábrica não deveria ser chamada");
        });

        assertThat(execucao.codigo()).isZero();
        assertThat(execucao.stdout()).contains("USO:").contains("CÓDIGOS DE SAÍDA:");
    }

    @Test
    void executarComHelpImprimeUsoERetornaZero() throws Exception {
        final Execucao execucao = executarCapturando(new String[] {"--help"}, cfg -> {
            throw new AssertionError("fábrica não deveria ser chamada");
        });

        assertThat(execucao.codigo()).isZero();
        assertThat(execucao.stdout()).contains("CÓDIGOS DE SAÍDA:");
    }

    @Test
    void executarComArgumentoDesconhecidoRetornaTres() throws Exception {
        final Execucao execucao = executarCapturando(new String[] {"--invalido=x"}, cfg -> {
            throw new AssertionError("fábrica não deveria ser chamada");
        });

        assertThat(execucao.codigo()).isEqualTo(ClassificadorFalhas.USO);
        assertThat(execucao.stderr())
                .contains("Erro de configuração: Argumento desconhecido: --invalido=x")
                .contains("exit 3");
    }

    @Test
    void executarComChaveInexistenteRetornaTres(@TempDir final Path tempDir) throws Exception {
        final String[] args = {
            "--client-id=hs-1",
            "--key=" + tempDir.resolve("nao-existe.pem"),
            "--cert=" + tempDir.resolve("tambem-nao.pem"),
            "--ig=hemograma",
            "--ig-versao=0.0.1",
        };

        final Execucao execucao = executarCapturando(args, cfg -> {
            throw new AssertionError("fábrica não deveria ser chamada");
        });

        assertThat(execucao.codigo()).isEqualTo(ClassificadorFalhas.USO);
        assertThat(execucao.stderr()).contains("Chave privada não encontrado");
    }

    @Test
    void executarComFalhaDeRedeRetornaQuatro(@TempDir final Path tempDir) throws Exception {
        final String[] args = argsComArquivos(tempDir);

        final Execucao execucao = executarCapturando(args, cfg -> {
            throw new IOException("connection refused");
        });

        assertThat(execucao.codigo()).isEqualTo(ClassificadorFalhas.INFRA);
        assertThat(execucao.stderr())
                .contains("Falha ao obter token: connection refused")
                .contains("INCONCLUSIVO")
                .contains("exit 4");
    }

    @Test
    void executarComRejeicaoDoServidorDeAutorizacaoRetornaSeis(@TempDir final Path tempDir) throws Exception {
        final String[] args = argsComArquivos(tempDir);
        final ClienteToken cliente = new ClienteToken("RS384", "https://auth.exemplo.gov.br/token", scope -> {
            throw new SmartTokenException("Falha ao obter token: HTTP 401 — {\"error\":\"invalid_client\"}");
        });

        final Execucao execucao = executarCapturando(args, cfg -> cliente);

        assertThat(execucao.codigo()).isEqualTo(ClassificadorFalhas.CREDENCIAL_NAO_APTA);
        assertThat(execucao.stderr())
                .contains("invalid_client")
                .contains("Credencial NÃO apta")
                .contains("exit 6");
    }

    @Test
    void executarComInterrupcaoRetornaQuatroERestauraFlag(@TempDir final Path tempDir) throws Exception {
        final String[] args = argsComArquivos(tempDir);
        final ClienteToken cliente = new ClienteToken("RS384", "https://auth.exemplo.gov.br/token", scope -> {
            throw new InterruptedException("interrompida");
        });

        final Execucao execucao = executarCapturando(args, cfg -> cliente);

        assertThat(execucao.codigo()).isEqualTo(ClassificadorFalhas.INFRA);
        assertThat(Thread.interrupted()).as("flag de interrupção restaurada").isTrue();
    }

    @Test
    void executarComDebugEmiteStackTraceNaFalha(@TempDir final Path tempDir) throws Exception {
        final String[] args = argsComArquivos(tempDir, "--debug");

        final Execucao execucao = executarCapturando(args, cfg -> {
            throw new IOException("connection refused");
        });

        assertThat(execucao.codigo()).isEqualTo(ClassificadorFalhas.INFRA);
        assertThat(execucao.stderr()).contains("java.io.IOException").contains("\tat ");
    }

    // ------------------------------------------------------------------
    // Utilitários de teste
    // ------------------------------------------------------------------

    private static String[] argsComArquivos(final Path tempDir, final String... extras) throws IOException {
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
        return args;
    }

    private static AnalisadorArgumentos.Config configComArquivos(final Path tempDir, final String... extras)
            throws IOException {
        return AnalisadorArgumentos.parseArgs(argsComArquivos(tempDir, extras));
    }

    /** Resultado de uma execução capturada: código de saída, stdout e stderr. */
    private record Execucao(int codigo, String stdout, String stderr) {}

    private static Execucao executarCapturando(final String[] args, final ClienteToken.Fabrica fabrica)
            throws Exception {
        final PrintStream outOriginal = System.out;
        final PrintStream errOriginal = System.err;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final int codigo;
        try (PrintStream outSubstituto = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream errSubstituto = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            System.setOut(outSubstituto);
            System.setErr(errSubstituto);
            codigo = VerificarAcesso.executar(args, fabrica);
        } finally {
            System.setOut(outOriginal);
            System.setErr(errOriginal);
        }
        return new Execucao(codigo, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
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
