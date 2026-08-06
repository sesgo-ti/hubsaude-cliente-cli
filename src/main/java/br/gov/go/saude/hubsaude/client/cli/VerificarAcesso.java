/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2025-2026 Estado de Goiás (SES-GO) e Universidade Federal de Goiás (UFG).
 */

package br.gov.go.saude.hubsaude.client.cli;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

import br.gov.go.saude.hubsaude.client.SmartTokenClient;

/**
 * Ferramenta de linha de comando para verificar acesso ao HubSaúde após credenciamento.
 *
 * <h2>Uso</h2>
 * <pre>{@code
 * # Via Maven (desenvolvimento)
 * mvn exec:java -Dexec.mainClass="br.gov.go.saude.hubsaude.client.cli.VerificarAcesso" \
 *     -Dexec.args="--client-id=hs-XXXXXXXX --key=chave.pem --cert=cert.pem --ig=hemograma --ig-versao=0.0.1"
 *
 * # Via JAR executável (fat JAR com todas as dependências)
 * java -jar hubsaude-cliente-cli-X.Y.Z.jar \
 *     --client-id=hs-XXXXXXXX --key=chave.pem --cert=cert.pem --ig=hemograma --ig-versao=0.0.1
 * }</pre>
 *
 * <h2>Parâmetros</h2>
 * <ul>
 *   <li>{@code --client-id} — ID do cliente (obrigatório)</li>
 *   <li>{@code --key} — Caminho para chave privada PEM (obrigatório)</li>
 *   <li>{@code --cert} — Caminho para certificado PEM (obrigatório)</li>
 *   <li>{@code --ig} — Alias do Guia de Implementação para o claim {@code hub_ctx} (obrigatório)</li>
 *   <li>{@code --ig-versao} — Versão SemVer completa do IG para o claim {@code hub_ctx} (obrigatório)</li>
 *   <li>{@code --endpoint} — URL do token endpoint (opcional, padrão: descoberta automática)</li>
 *   <li>{@code --fhir-base} — URL base FHIR para descoberta (opcional, padrão: https://fhir.saude.go.gov.br)</li>
 *   <li>{@code --scope} — Scope a solicitar (opcional, padrão: system/Patient.rs)</li>
 *   <li>{@code --password} — Senha da chave privada, se criptografada (opcional)</li>
 *   <li>{@code --tls} — Protocolo TLS: TLSv1.3 ou TLSv1.2 (opcional, padrão: TLSv1.3)</li>
 *   <li>{@code --alg} — Algoritmo JWT aceito pelo HubSaúde: RS384 ou ES384 (opcional, padrão: RS384)</li>
 *   <li>{@code --kid} — Identificador da chave registrada no SA (header {@code kid}, opcional)</li>
 *   <li>{@code --trust} — Caminho para certificado PEM do servidor (trust anchor), para simulador/homologação</li>
 *   <li>{@code --quiet} — Oculta os detalhes do token (por padrão são exibidos)</li>
 *   <li>{@code --show-token} — Exibe o access token completo (sensível; oculto por padrão)</li>
 *   <li>{@code --debug} — Exibe detalhes técnicos (stack trace) em caso de erro</li>
 * </ul>
 *
 * <h2>Códigos de saída</h2>
 * <p>A ferramenta adota a taxonomia do {@code hubsaude-cli} para o caso de
 * uso {@code auth verify} (issue #817): {@code 0} sucesso (credencial apta),
 * {@code 3} erro de uso/configuração, {@code 4} falha de infraestrutura de
 * rede (teste inconclusivo) e {@code 6} credencial não apta (rejeição
 * determinística). Ver {@link ClassificadorFalhas}.</p>
 *
 * <p>As responsabilidades desta classe são apenas de orquestração; o parsing
 * e a validação dos argumentos vivem em {@link AnalisadorArgumentos}, a
 * formatação de saída em {@link SaidaConsole}, a classificação das falhas
 * em código de saída em {@link ClassificadorFalhas} e a chamada ao
 * {@code SmartTokenClient} atrás da costura {@link ClienteToken}
 * (issue #1034).</p>
 */
public final class VerificarAcesso {

    private VerificarAcesso() {
        // Utilitário não instanciável
    }

    /**
     * Ponto de entrada da aplicação: delega a {@link #executar} e converte
     * o código retornado em código de processo.
     *
     * @param args argumentos de linha de comando
     */
    public static void main(final String[] args) {
        System.exit(executar(args, ClienteToken::conectar));
    }

    /**
     * Executa o fluxo completo da CLI e decide o código de saída conforme a
     * taxonomia da issue #817 — método puro em relação a
     * {@code System.exit}, para que o contrato de códigos de saída seja
     * testável por unidade com uma {@link ClienteToken.Fabrica} fake.
     *
     * @param args argumentos de linha de comando
     * @param fabrica fábrica do cliente de token
     * @return código de saída do processo ({@link ClassificadorFalhas#SUCESSO},
     *         {@link ClassificadorFalhas#USO}, {@link ClassificadorFalhas#INFRA}
     *         ou {@link ClassificadorFalhas#CREDENCIAL_NAO_APTA})
     */
    static int executar(final String[] args, final ClienteToken.Fabrica fabrica) {
        SaidaConsole.printBanner();

        if (args.length == 0 || AnalisadorArgumentos.hasFlag(args, "--help", "-h")) {
            SaidaConsole.printUsage();
            return ClassificadorFalhas.SUCESSO;
        }

        try {
            final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(args);
            verificarAcesso(config, fabrica);
            return ClassificadorFalhas.SUCESSO;
        } catch (IllegalArgumentException e) {
            SaidaConsole.printError("Erro de configuração: " + e.getMessage());
            SaidaConsole.printVeredito(ClassificadorFalhas.USO);
            return ClassificadorFalhas.USO;
        } catch (Exception e) {
            return reportarEClassificar(e, AnalisadorArgumentos.hasFlag(args, "--debug"));
        }
    }

    /**
     * Restaura a flag de interrupção quando aplicável, classifica a falha
     * na taxonomia da issue #817 e a reporta no console com o veredito.
     *
     * @param e falha capturada
     * @param debug se {@code true}, emite os detalhes técnicos
     * @return código de saída classificado
     */
    private static int reportarEClassificar(final Exception e, final boolean debug) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        final int codigo = ClassificadorFalhas.classificar(e);
        SaidaConsole.reportarFalha(e, codigo, debug);
        return codigo;
    }

    /**
     * Executa a verificação de acesso: valida os arquivos configurados, cria
     * o cliente pela fábrica dada, obtém o token e exibe o resultado.
     *
     * <p>Visibilidade de pacote para teste unitário direto do fluxo com uma
     * {@link ClienteToken.Fabrica} fake (issue #1034), sem rede.</p>
     *
     * @param config configuração parseada
     * @param fabrica fábrica do cliente de token
     * @throws IOException em caso de erro de rede, de protocolo ou de leitura
     *         dos arquivos PEM
     * @throws InterruptedException se a thread for interrompida durante a
     *         requisição
     */
    static void verificarAcesso(final AnalisadorArgumentos.Config config, final ClienteToken.Fabrica fabrica)
            throws IOException, InterruptedException {
        // Validar arquivos primeiro (parse já garantiu presença; revalida a borda)
        final String keyPath = Objects.requireNonNull(config.keyPath, "--key é obrigatório");
        final String certPath = Objects.requireNonNull(config.certPath, "--cert é obrigatório");
        AnalisadorArgumentos.validateFile(keyPath, "Chave privada");
        AnalisadorArgumentos.validateFile(certPath, "Certificado");

        final ClienteToken cliente = fabrica.criar(config);

        // Exibir configuração com o endpoint efetivamente usado
        SaidaConsole.printConfiguracao(config, cliente.jwtAlgorithm(), cliente.tokenEndpoint());

        SaidaConsole.printInfo("Obtendo token de acesso...");
        // Latência com fonte monotônica: Instant.now() sofre saltos de
        // NTP/relógio de parede (#1817).
        final long startNanos = System.nanoTime();

        final SmartTokenClient.TokenResponse resposta = cliente.obterToken(config.scope);

        final Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

        // Sucesso!
        SaidaConsole.printResultado(resposta, elapsed, config.quiet, config.showToken);
    }
}
