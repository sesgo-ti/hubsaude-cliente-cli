/*
 * Copyright (c) 2026 SES-GO / UFG
 * Todos os direitos reservados.
 */

package br.gov.go.saude.hubsaude.client.cli;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

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
 *   <li>{@code --debug} — Exibe detalhes técnicos (stack trace) em caso de erro</li>
 * </ul>
 *
 * <p>As responsabilidades desta classe são apenas de orquestração; o parsing
 * e a validação dos argumentos vivem em {@link AnalisadorArgumentos}, a
 * formatação de saída em {@link SaidaConsole} e a chamada ao
 * {@code SmartTokenClient} atrás da costura {@link ClienteToken}
 * (issue #1034).</p>
 */
public final class VerificarAcesso {

    private VerificarAcesso() {
        // Utilitário não instanciável
    }

    /**
     * Ponto de entrada da aplicação.
     *
     * @param args argumentos de linha de comando
     */
    public static void main(final String[] args) {
        SaidaConsole.printBanner();

        if (args.length == 0 || AnalisadorArgumentos.hasFlag(args, "--help", "-h")) {
            SaidaConsole.printUsage();
            System.exit(0);
        }

        try {
            final AnalisadorArgumentos.Config config = AnalisadorArgumentos.parseArgs(args);
            verificarAcesso(config, ClienteToken::conectar);
        } catch (IllegalArgumentException e) {
            SaidaConsole.printError("Erro de configuração: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            SaidaConsole.reportarFalha(e, AnalisadorArgumentos.hasFlag(args, "--debug"));
            System.exit(1);
        }
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
        // Validar arquivos primeiro
        AnalisadorArgumentos.validateFile(config.keyPath, "Chave privada");
        AnalisadorArgumentos.validateFile(config.certPath, "Certificado");

        final ClienteToken cliente = fabrica.criar(config);

        // Exibir configuração com o endpoint efetivamente usado
        SaidaConsole.printConfiguracao(config, cliente.jwtAlgorithm(), cliente.tokenEndpoint());

        SaidaConsole.printInfo("Obtendo token de acesso...");
        final Instant start = Instant.now();

        final SmartTokenClient.TokenResponse resposta = cliente.obterToken(config.scope);

        final Duration elapsed = Duration.between(start, Instant.now());

        // Sucesso!
        SaidaConsole.printResultado(resposta, elapsed, config.quiet);
    }
}
