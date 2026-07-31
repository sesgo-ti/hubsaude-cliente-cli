/*
 * Copyright (c) 2026 SES-GO / UFG
 * Todos os direitos reservados.
 */

package br.gov.go.saude.hubsaude.client.cli;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.jspecify.annotations.Nullable;

import br.gov.go.saude.hubsaude.client.SmartTokenClient;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Formata e emite a saída de console do {@link VerificarAcesso}: banner,
 * instruções de uso, mensagens padronizadas ({@code [i]}, {@code [OK]},
 * {@code [X]}), tabelas do token JWT e da resposta do servidor de
 * autorização, e o relatório de falha.
 *
 * <p>Colaborador extraído de {@code VerificarAcesso} (issue #1034). A
 * escrita direta em {@code System.out}/{@code System.err} é o mecanismo
 * legítimo de IO desta CLI (não há logger na saída para o operador); os
 * segredos são redigidos antes de qualquer impressão
 * ({@link #redactSecrets(String)}).</p>
 */
@SuppressWarnings("PMD.SystemPrintln") // console é a interface de usuário desta CLI (sem logger na saída ao operador)
final class SaidaConsole {

    private static final int TOKEN_PREVIEW_CHARS = 50;
    private static final int TOKEN_LINE_WIDTH = 80;

    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";

    private SaidaConsole() {
        // Utilitário não instanciável
    }

    /**
     * Imprime o banner de abertura da ferramenta.
     */
    static void printBanner() {
        System.out.println();
        System.out.println(ANSI_BOLD + "╔═══════════════════════════════════════════════════════════╗" + ANSI_RESET);
        System.out.println(ANSI_BOLD + "║     HubSaúde - Verificador de Acesso (Pós-Credenciamento) ║" + ANSI_RESET);
        System.out.println(ANSI_BOLD + "╚═══════════════════════════════════════════════════════════╝" + ANSI_RESET);
        System.out.println();
    }

    /**
     * Imprime as instruções de uso (parâmetros, exemplos e códigos de saída).
     */
    @SuppressWarnings("checkstyle:MethodLength")
    // Texto de ajuda da CLI impresso linha a linha: conteúdo declarativo,
    // sem lógica — dividir só fragmentaria a mensagem.
    static void printUsage() {
        System.out.println("Verifica se o credenciamento foi aprovado obtendo um token de acesso.");
        System.out.println();
        System.out.println(ANSI_BOLD + "USO:" + ANSI_RESET);
        System.out.println("  java -jar hubsaude-cliente-cli-X.Y.Z.jar \\");
        System.out.println("      --client-id=<ID> --key=<CHAVE.PEM> --cert=<CERT.PEM> \\");
        System.out.println("      --ig=<ALIAS> --ig-versao=<SEMVER> [opções]");
        System.out.println();
        System.out.println(ANSI_BOLD + "PARÂMETROS OBRIGATÓRIOS:" + ANSI_RESET);
        System.out.println("  --client-id=<ID>      ID do cliente (fornecido no credenciamento)");
        System.out.println("  --key=<PATH>          Caminho para a chave privada PEM");
        System.out.println("  --cert=<PATH>         Caminho para o certificado PEM");
        System.out.println("  --ig=<ALIAS>          Alias do Guia de Implementação (claim hub_ctx)");
        System.out.println("  --ig-versao=<SEMVER>  Versão SemVer completa do IG (ex.: 0.0.1)");
        System.out.println();
        System.out.println(ANSI_BOLD + "PARÂMETROS OPCIONAIS:" + ANSI_RESET);
        System.out.println("  --endpoint=<URL>      URL do token endpoint (desabilita descoberta)");
        System.out.println(
                "  --fhir-base=<URL>     URL base FHIR (padrão: " + AnalisadorArgumentos.DEFAULT_FHIR_BASE + ")");
        System.out.println(
                "  --scope=<SCOPE>       Scope a solicitar (padrão: " + AnalisadorArgumentos.DEFAULT_SCOPE + ")");
        System.out.println("  --password=<SENHA>    Senha da chave privada (se criptografada)");
        System.out.println("  --tls=<VERSAO>        Protocolo TLS: TLSv1.3 ou TLSv1.2 (padrão: "
                + AnalisadorArgumentos.DEFAULT_TLS + ")");
        System.out.println("  --alg=<ALG>           Algoritmo JWT: RS384 ou ES384 (padrão: "
                + AnalisadorArgumentos.DEFAULT_ALG + ")");
        System.out.println("  --kid=<KID>           Identificador da chave registrada no SA (header kid)");
        System.out.println(
                "  --trust=<PATH>        Certificado PEM do servidor " + "(trust anchor, para simulador/homologação)");
        System.out.println("  --quiet, -q           Oculta os detalhes do token (exibidos por padrão)");
        System.out.println("  --show-token          Exibe o access token completo (sensível; oculto por padrão)");
        System.out.println("  --debug               Exibe detalhes técnicos (stack trace) em caso de erro");
        System.out.println("  --help, -h            Exibe esta mensagem");
        System.out.println();
        System.out.println(ANSI_BOLD + "EXEMPLOS:" + ANSI_RESET);
        System.out.println();
        System.out.println("  # Verificação básica (descoberta automática de endpoint)");
        System.out.println("  java -jar hubsaude-cliente-cli-0.0.0-SNAPSHOT.jar \\");
        System.out.println("      --client-id=hs-12345678 \\");
        System.out.println("      --key=minha-chave.pem \\");
        System.out.println("      --cert=meu-certificado.pem \\");
        System.out.println("      --ig=hemograma \\");
        System.out.println("      --ig-versao=0.0.1");
        System.out.println();
        System.out.println("  # Com endpoint explícito, TLS 1.2 e algoritmo ES384");
        System.out.println("  java -jar hubsaude-cliente-cli-0.0.0-SNAPSHOT.jar \\");
        System.out.println("      --client-id=hs-12345678 \\");
        System.out.println("      --key=minha-chave.pem \\");
        System.out.println("      --cert=meu-certificado.pem \\");
        System.out.println("      --ig=hemograma \\");
        System.out.println("      --ig-versao=0.0.1 \\");
        System.out.println("      --endpoint=https://fhir.saude.go.gov.br/auth/token \\");
        System.out.println("      --alg=ES384 \\");
        System.out.println("      --tls=TLSv1.2 \\");
        System.out.println("      --quiet");
        System.out.println();
        System.out.println(ANSI_BOLD + "CÓDIGOS DE SAÍDA:" + ANSI_RESET);
        System.out.println("  0  Sucesso - token obtido, credencial apta");
        System.out.println("  3  Erro de uso - argumentos, arquivos ou configuração inválidos");
        System.out.println("  4  Infraestrutura de rede - teste INCONCLUSIVO (discovery, timeout, 429, 5xx);");
        System.out.println("     repita mais tarde");
        System.out.println("  6  Credencial NÃO apta - rejeição determinística (HTTP 400/401 invalid_client");
        System.out.println("     ou handshake mTLS); acione o processo de credenciamento");
        System.out.println();
    }

    /**
     * Exibe a configuração efetiva da verificação, incluindo o endpoint
     * realmente usado (explícito ou descoberto via {@code .well-known}).
     *
     * @param config configuração parseada
     * @param jwtAlgorithm algoritmo JWT efetivo do cliente
     * @param tokenEndpoint token endpoint efetivo do cliente
     */
    static void printConfiguracao(
            final AnalisadorArgumentos.Config config, final String jwtAlgorithm, final String tokenEndpoint) {
        printInfo("Configuração:");
        System.out.println("  Client ID:  " + config.clientId);
        System.out.println("  Chave:      " + config.keyPath);
        System.out.println("  Certificado:" + config.certPath);
        System.out.println("  Scope:      " + config.scope);
        System.out.println("  IG:         " + config.ig + " (versão " + config.igVersao + ")");
        System.out.println("  TLS:        " + config.tlsProtocol);
        System.out.println("  Algoritmo:  " + jwtAlgorithm);
        if (config.tokenEndpoint == null) {
            System.out.println("  FHIR Base:  " + config.fhirBase);
            System.out.println("  Endpoint:   " + tokenEndpoint + " (descoberto via .well-known)");
        } else {
            System.out.println("  Endpoint:   " + tokenEndpoint);
        }
        System.out.println();
    }

    /**
     * Exibe o resultado da verificação bem-sucedida: resposta do servidor de
     * autorização (redigida), tempo de resposta e detalhes (ou prévia, em
     * modo {@code --quiet}) do token. O conteúdo integral do token só é
     * exibido quando {@code showToken} é verdadeiro ({@code --show-token}),
     * para a credencial não vazar em terminal/logs de CI (issue #813).
     *
     * @param resposta resposta de token do servidor de autorização
     * @param elapsed duração da obtenção do token
     * @param quiet se {@code true}, oculta os detalhes do token
     * @param showToken se {@code true}, exibe o token completo
     */
    static void printResultado(
            final SmartTokenClient.TokenResponse resposta,
            final Duration elapsed,
            final boolean quiet,
            final boolean showToken) {
        final String token = resposta.accessToken();

        printSuccess("Token obtido com sucesso!");
        System.out.println();

        if (!quiet) {
            printKeycloakResponse(resposta.rawJson());
        }

        System.out.println("  Tempo de resposta: " + elapsed.toMillis() + "ms");

        if (quiet) {
            printTokenPreview(token);
            System.out.println();
            printInfo("Omita --quiet para ver detalhes do token.");
        } else {
            printTokenDetails(token, showToken);
        }

        System.out.println();
        printSuccess("Credenciamento verificado. Acesso ao HubSaúde está funcionando.");
    }

    /**
     * Exibe a prévia truncada do token ({@value #TOKEN_PREVIEW_CHARS}
     * primeiros caracteres), sem a assinatura — suficiente para conferência
     * visual sem expor a credencial inteira.
     *
     * @param token JWT compacto
     */
    private static void printTokenPreview(final String token) {
        final String preview = token.substring(0, Math.min(TOKEN_PREVIEW_CHARS, token.length()));
        System.out.println("  Token (primeiros " + TOKEN_PREVIEW_CHARS + " chars): " + preview + "...");
    }

    /**
     * Exibe, no mesmo formato de tabela usado para o JWT, o corpo JSON da
     * resposta do servidor de autorização (Keycloak). O valor de
     * {@code access_token} (e de outros segredos, se presentes) é substituído
     * por um placeholder para não vazar o token na saída.
     *
     * @param rawJson corpo JSON cru da resposta; pode ser {@code null} quando
     *                o token foi servido a partir do cache
     */
    static void printKeycloakResponse(final @Nullable String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return;
        }
        printInfo("Resposta do servidor de autorização (Keycloak):");
        System.out.println();
        System.out.print(JwtPayloadFormatter.formatAsTable(redactSecrets(rawJson), "    "));
        System.out.println();
    }

    /**
     * Substitui os valores de campos sensíveis ({@code access_token},
     * {@code refresh_token}, {@code id_token}) por um placeholder.
     *
     * <p>A redação de segredos é lógica pura e sensível à segurança
     * (issue #986): o token jamais deve vazar no terminal.</p>
     *
     * @param json JSON original
     * @return JSON com os segredos redigidos
     */
    static String redactSecrets(final String json) {
        return json.replaceAll("(\"(?:access_token|refresh_token|id_token)\"\\s*:\\s*\")[^\"]*\"", "$1<REDACTED>\"");
    }

    /**
     * Exibe header e payload decodificados do token JWT. Por padrão a
     * exibição termina com a prévia truncada do token
     * ({@value #TOKEN_PREVIEW_CHARS} caracteres); o conteúdo integral —
     * incluindo a assinatura — só é impresso quando {@code showToken} é
     * verdadeiro ({@code --show-token}), alinhado à US-14 do
     * {@code hubsaude-cli} (issue #813).
     *
     * @param token JWT compacto ({@code header.payload.assinatura})
     * @param showToken se {@code true}, imprime o token completo
     */
    @SuppressFBWarnings(
            value = "REC_CATCH_EXCEPTION",
            justification = "Decodificação do JWT é best-effort para exibição; "
                    + "qualquer falha cai no fallback amigável sem abortar.")
    static void printTokenDetails(final String token, final boolean showToken) {
        System.out.println();
        printInfo("Detalhes do token JWT:");

        try {
            final String[] parts = token.split("\\.", -1);
            if (parts.length >= 2) {
                final String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                final String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

                System.out.println();
                System.out.println("  Header:");
                System.out.print(JwtPayloadFormatter.formatAsTable(headerJson, "    "));

                System.out.println();
                System.out.println("  Payload:");
                System.out.print(JwtPayloadFormatter.formatAsTable(payloadJson, "    "));
            }
        } catch (Exception e) {
            System.out.println("  (Não foi possível decodificar o token)");
        }

        System.out.println();
        if (showToken) {
            System.out.println("  Token completo:");
            for (int i = 0; i < token.length(); i += TOKEN_LINE_WIDTH) {
                System.out.println("    " + token.substring(i, Math.min(i + TOKEN_LINE_WIDTH, token.length())));
            }
        } else {
            printTokenPreview(token);
            printInfo("Use --show-token para exibir o token completo.");
        }
    }

    /**
     * Reporta uma falha de obtenção de token no stderr com mensagem curta e
     * acionável, seguida do veredito da classificação (código de saída da
     * taxonomia da issue #817); os detalhes técnicos (stack trace) são
     * emitidos apenas em modo diagnóstico.
     *
     * @param e falha ocorrida
     * @param codigoSaida código de saída classificado
     *        ({@link ClassificadorFalhas#classificar(Exception)})
     * @param debug se {@code true}, emite os detalhes técnicos
     */
    static void reportarFalha(final Exception e, final int codigoSaida, final boolean debug) {
        printError("Falha ao obter token: " + e.getMessage());
        printVeredito(codigoSaida);
        if (debug) {
            System.err.println(formatarStackTrace(e));
        } else {
            System.err.println(ANSI_YELLOW + "[i] Execute com --debug para exibir os detalhes técnicos." + ANSI_RESET);
        }
    }

    /**
     * Imprime no stderr o veredito correspondente ao código de saída da
     * taxonomia da issue #817, orientando a ação da automação (acionar o
     * credenciamento, reexecutar mais tarde ou corrigir a configuração).
     *
     * @param codigoSaida código de saída classificado
     */
    static void printVeredito(final int codigoSaida) {
        switch (codigoSaida) {
            case ClassificadorFalhas.CREDENCIAL_NAO_APTA ->
                printError("Credencial NÃO apta: rejeição determinística do servidor de autorização"
                        + " (HTTP 400/401 invalid_client ou handshake mTLS) — exit 6.");
            case ClassificadorFalhas.INFRA ->
                System.err.println(ANSI_YELLOW
                        + "[i] Teste INCONCLUSIVO: falha de infraestrutura de rede — repita mais tarde (exit 4)."
                        + ANSI_RESET);
            case ClassificadorFalhas.USO ->
                System.err.println(ANSI_YELLOW
                        + "[i] Erro de uso: verifique os argumentos e arquivos informados (exit 3)."
                        + ANSI_RESET);
            default -> {
                // Sucesso (0) não tem veredito de falha a imprimir
            }
        }
    }

    private static String formatarStackTrace(final Throwable falha) {
        final StringBuilder sb = new StringBuilder();
        Throwable atual = falha;
        String prefixo = "";
        while (atual != null) {
            sb.append(prefixo).append(atual);
            for (StackTraceElement quadro : atual.getStackTrace()) {
                sb.append(System.lineSeparator()).append("\tat ").append(quadro);
            }
            final Throwable proxima = atual.getCause();
            atual = proxima == atual ? null : proxima;
            prefixo = System.lineSeparator() + "Causado por: ";
        }
        return sb.toString();
    }

    /**
     * Imprime mensagem informativa com o prefixo {@code [i]}.
     *
     * @param message mensagem a exibir
     */
    static void printInfo(final String message) {
        System.out.println(ANSI_YELLOW + "[i] " + message + ANSI_RESET);
    }

    /**
     * Imprime mensagem de sucesso com o prefixo {@code [OK]}.
     *
     * @param message mensagem a exibir
     */
    static void printSuccess(final String message) {
        System.out.println(ANSI_GREEN + "[OK] " + message + ANSI_RESET);
    }

    /**
     * Imprime mensagem de erro em {@code stderr} com o prefixo {@code [X]}.
     *
     * @param message mensagem a exibir
     */
    static void printError(final String message) {
        System.err.println(ANSI_RED + "[X] " + message + ANSI_RESET);
    }
}
