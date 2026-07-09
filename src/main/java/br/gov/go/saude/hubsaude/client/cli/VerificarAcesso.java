/*
 * Copyright (c) 2026 SES-GO / UFG
 * Todos os direitos reservados.
 */

package br.gov.go.saude.hubsaude.client.cli;

import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import br.gov.go.saude.hubsaude.client.SmartTokenClient;
import br.gov.go.saude.hubsaude.client.SmartTokenClientBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Ferramenta de linha de comando para verificar acesso ao HubSaúde após credenciamento.
 *
 * <h2>Uso</h2>
 * <pre>{@code
 * # Via Maven (desenvolvimento)
 * mvn exec:java -Dexec.mainClass="br.gov.go.saude.hubsaude.client.cli.VerificarAcesso" \
 *     -Dexec.args="--client-id=hs-XXXXXXXX --key=chave.pem --cert=cert.pem"
 *
 * # Via JAR executável (fat JAR com todas as dependências)
 * java -jar hubsaude-cliente-cli-X.Y.Z.jar \
 *     --client-id=hs-XXXXXXXX --key=chave.pem --cert=cert.pem
 * }</pre>
 *
 * <h2>Parâmetros</h2>
 * <ul>
 *   <li>{@code --client-id} — ID do cliente (obrigatório)</li>
 *   <li>{@code --key} — Caminho para chave privada PEM (obrigatório)</li>
 *   <li>{@code --cert} — Caminho para certificado PEM (obrigatório)</li>
 *   <li>{@code --endpoint} — URL do token endpoint (opcional, padrão: descoberta automática)</li>
 *   <li>{@code --fhir-base} — URL base FHIR para descoberta (opcional, padrão: https://fhir.saude.go.gov.br)</li>
 *   <li>{@code --scope} — Scope a solicitar (opcional, padrão: system/Patient.rs)</li>
 *   <li>{@code --password} — Senha da chave privada, se criptografada (opcional)</li>
 *   <li>{@code --tls} — Protocolo TLS: TLSv1.3 ou TLSv1.2 (opcional, padrão: TLSv1.3)</li>
 *   <li>{@code --alg} — Algoritmo JWT: RS256, RS384, RS512, PS256, etc. (opcional, padrão: RS256)</li>
 *   <li>{@code --trust} — Caminho para certificado PEM do servidor (trust anchor), para simulador/homologação</li>
 *   <li>{@code --quiet} — Oculta os detalhes do token (por padrão são exibidos)</li>
 * </ul>
 */
// PMD.GodClass: TODO débito técnico — extrair parsing de argumentos, IO e
// validação em colaboradores dedicados.
@SuppressWarnings("PMD.GodClass")
public final class VerificarAcesso {

    private static final String DEFAULT_FHIR_BASE = "https://hub.saude.go.gov.br";
    private static final String DEFAULT_SCOPE = "system/Patient.rs";
    private static final String DEFAULT_TLS = "TLSv1.3";
    private static final String DEFAULT_ALG = "RS256";
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 60;
    private static final int TOKEN_PREVIEW_CHARS = 50;
    private static final int TOKEN_LINE_WIDTH = 80;

    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";

    private VerificarAcesso() {
        // Utilitário não instanciável
    }

    /**
     * Ponto de entrada da aplicação.
     *
     * @param args argumentos de linha de comando
     */
    @SuppressFBWarnings(
            value = "INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE",
            justification = "Ferramenta de diagnóstico CLI: o stack trace só é "
                    + "exibido em modo verbose (sem --quiet) para o próprio "
                    + "operador, não há superfície remota; a saída destina-se "
                    + "ao próprio operador no terminal.")
    public static void main(final String[] args) {
        printBanner();

        if (args.length == 0 || hasFlag(args, "--help", "-h")) {
            printUsage();
            System.exit(0);
        }

        try {
            final Config config = parseArgs(args);
            verificarAcesso(config);
        } catch (IllegalArgumentException e) {
            printError("Erro de configuração: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            printError("Falha ao obter token: " + e.getMessage());
            if (!hasFlag(args, "--quiet", "-q")) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    // PMD.SignatureDeclareThrowsException: ponto de entrada CLI; loga e
    // encerra o processo no chamador.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    @SuppressFBWarnings(
            value = "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION",
            justification = "Ponto de entrada CLI: agrega exceções do cliente "
                    + "SMART; o chamador (main) trata e encerra o processo.")
    private static void verificarAcesso(final Config config) throws Exception {
        // Validar arquivos primeiro
        validateFile(config.keyPath, "Chave privada");
        validateFile(config.certPath, "Certificado");

        // Construir cliente
        final SmartTokenClientBuilder builder = SmartTokenClient.builder()
                .clientId(config.clientId)
                .privateKeyPem(Path.of(config.keyPath))
                .certificatePem(Path.of(config.certPath))
                .tlsProtocol(config.tlsProtocol)
                .jwtAlgorithm(config.jwtAlgorithm)
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .requestTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS));

        final boolean usandoDescoberta = config.tokenEndpoint == null;
        if (usandoDescoberta) {
            builder.fhirBase(config.fhirBase);
        } else {
            builder.tokenEndpoint(config.tokenEndpoint);
        }

        if (config.keyPassword != null) {
            builder.privateKeyPassword(config.keyPassword);
        }

        if (config.trustAnchorPath != null) {
            validateFile(config.trustAnchorPath, "Trust anchor");
            builder.serverTrustAnchor(Path.of(config.trustAnchorPath));
        }

        final SmartTokenClient client = builder.build();

        // Exibir configuração com o endpoint efetivamente usado
        printInfo("Configuração:");
        System.out.println("  Client ID:  " + config.clientId);
        System.out.println("  Chave:      " + config.keyPath);
        System.out.println("  Certificado:" + config.certPath);
        System.out.println("  Scope:      " + config.scope);
        System.out.println("  TLS:        " + config.tlsProtocol);
        System.out.println("  Algoritmo:  " + client.getJwtAlgorithm());
        if (usandoDescoberta) {
            System.out.println("  FHIR Base:  " + config.fhirBase);
            System.out.println("  Endpoint:   " + client.getTokenEndpoint() + " (descoberto via .well-known)");
        } else {
            System.out.println("  Endpoint:   " + client.getTokenEndpoint());
        }
        System.out.println();

        printInfo("Obtendo token de acesso...");
        final Instant start = Instant.now();

        final SmartTokenClient.TokenResponse resposta = client.obtainTokenResponse(config.scope);
        final String token = resposta.accessToken();

        final Duration elapsed = Duration.between(start, Instant.now());

        // Sucesso!
        printSuccess("Token obtido com sucesso!");
        System.out.println();

        if (!config.quiet) {
            printKeycloakResponse(resposta.rawJson());
        }

        System.out.println("  Tempo de resposta: " + elapsed.toMillis() + "ms");

        if (config.quiet) {
            final String preview = token.substring(0, Math.min(TOKEN_PREVIEW_CHARS, token.length()));
            System.out.println("  Token (primeiros " + TOKEN_PREVIEW_CHARS + " chars): " + preview + "...");
            System.out.println();
            printInfo("Omita --quiet para ver detalhes do token.");
        } else {
            printTokenDetails(token);
        }

        System.out.println();
        printSuccess("Credenciamento verificado. Acesso ao HubSaúde está funcionando.");
    }

    /**
     * Exibe, no mesmo formato de tabela usado para o JWT, o corpo JSON da
     * resposta do servidor de autorização (Keycloak). O valor de
     * {@code access_token} (e de outros segredos, se presentes) é substituído
     * por um placeholder para não vazar o token na saída.
     *
     * <p>Visibilidade de pacote para teste unitário direto (issue #986).</p>
     *
     * @param rawJson corpo JSON cru da resposta; pode ser {@code null} quando
     *                o token foi servido a partir do cache
     */
    static void printKeycloakResponse(final String rawJson) {
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
     * <p>Visibilidade de pacote para teste unitário direto (issue #986):
     * a redação de segredos é lógica pura e sensível à segurança.</p>
     *
     * @param json JSON original
     * @return JSON com os segredos redigidos
     */
    static String redactSecrets(final String json) {
        return json.replaceAll("(\"(?:access_token|refresh_token|id_token)\"\\s*:\\s*\")[^\"]*\"", "$1<REDACTED>\"");
    }

    /**
     * Exibe header, payload e conteúdo integral do token JWT.
     *
     * <p>Visibilidade de pacote para teste unitário direto (issue #986).</p>
     *
     * @param token JWT compacto ({@code header.payload.assinatura})
     */
    @SuppressFBWarnings(
            value = "REC_CATCH_EXCEPTION",
            justification = "Decodificação do JWT é best-effort para exibição; "
                    + "qualquer falha cai no fallback amigável sem abortar.")
    static void printTokenDetails(final String token) {
        System.out.println();
        printInfo("Detalhes do token JWT:");

        try {
            final String[] parts = token.split("\\.");
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
        System.out.println("  Token completo:");
        for (int i = 0; i < token.length(); i += TOKEN_LINE_WIDTH) {
            System.out.println("    " + token.substring(i, Math.min(i + TOKEN_LINE_WIDTH, token.length())));
        }
    }

    /**
     * Valida que o arquivo existe e é legível.
     *
     * <p>Visibilidade de pacote para teste unitário direto (issue #986).</p>
     *
     * @param path caminho do arquivo
     * @param description descrição usada na mensagem de erro
     * @throws IllegalArgumentException se o arquivo não existir ou não puder ser lido
     */
    static void validateFile(final String path, final String description) {
        final Path file = Path.of(path);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException(description + " não encontrado: " + path);
        }
        if (!Files.isReadable(file)) {
            throw new IllegalArgumentException(description + " não pode ser lido: " + path);
        }
    }

    /**
     * Interpreta os argumentos de linha de comando e monta a configuração,
     * aplicando os valores padrão e validando os parâmetros obrigatórios.
     *
     * <p>Visibilidade de pacote para teste unitário direto do parsing
     * (issue #986), sem expor API pública.</p>
     *
     * @param args argumentos da linha de comando
     * @return configuração montada
     * @throws IllegalArgumentException se houver argumento desconhecido ou
     *         parâmetro obrigatório ausente
     */
    static Config parseArgs(final String... args) {
        final Config config = new Config();

        for (final String arg : args) {
            parseArgument(arg, config);
        }

        // Tentar ler senha do console se não fornecida e chave parecer criptografada
        if (config.keyPassword == null && config.keyPath != null) {
            config.keyPassword = promptPasswordIfNeeded(config.keyPath);
        }

        validateRequiredArgs(config);
        return config;
    }

    @SuppressWarnings("PMD.CyclomaticComplexity") // simples cadeia if/else if para parsing CLI
    private static void parseArgument(final String arg, final Config config) {
        if (arg.startsWith("--client-id=")) {
            config.clientId = extractValue(arg, "--client-id=");
        } else if (arg.startsWith("--key=")) {
            config.keyPath = extractValue(arg, "--key=");
        } else if (arg.startsWith("--cert=")) {
            config.certPath = extractValue(arg, "--cert=");
        } else if (arg.startsWith("--endpoint=")) {
            config.tokenEndpoint = extractValue(arg, "--endpoint=");
        } else if (arg.startsWith("--fhir-base=")) {
            config.fhirBase = extractValue(arg, "--fhir-base=");
        } else if (arg.startsWith("--scope=")) {
            config.scope = extractValue(arg, "--scope=");
        } else if (arg.startsWith("--password=")) {
            config.keyPassword = extractValue(arg, "--password=").toCharArray();
        } else if (arg.startsWith("--tls=")) {
            config.tlsProtocol = extractValue(arg, "--tls=");
        } else if (arg.startsWith("--alg=")) {
            config.jwtAlgorithm = extractValue(arg, "--alg=").toUpperCase(java.util.Locale.ROOT);
        } else if (arg.startsWith("--trust=")) {
            config.trustAnchorPath = extractValue(arg, "--trust=");
        } else if ("--quiet".equals(arg) || "-q".equals(arg)) {
            config.quiet = true;
        } else if (!"--help".equals(arg) && !"-h".equals(arg)) {
            throw new IllegalArgumentException("Argumento desconhecido: " + arg);
        }
    }

    private static String extractValue(final String arg, final String prefix) {
        return arg.substring(prefix.length());
    }

    private static void validateRequiredArgs(final Config config) {
        if (config.clientId == null) {
            throw new IllegalArgumentException("--client-id é obrigatório");
        }
        if (config.keyPath == null) {
            throw new IllegalArgumentException("--key é obrigatório");
        }
        if (config.certPath == null) {
            throw new IllegalArgumentException("--cert é obrigatório");
        }
    }

    /**
     * Tenta detectar se a chave é criptografada e solicita senha se necessário.
     *
     * @param keyPath caminho da chave
     * @return senha digitada ou null se não necessária/não disponível
     */
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull") // null = sem senha, não senha vazia
    @SuppressFBWarnings(
            value = {"PZLA_PREFER_ZERO_LENGTH_ARRAYS", "REC_CATCH_EXCEPTION"},
            justification = "null representa 'sem senha' (semântica distinta de "
                    + "senha vazia char[0]); o catch genérico ignora falhas de "
                    + "leitura, tratadas adiante na validação dos arquivos.")
    private static char[] promptPasswordIfNeeded(final String keyPath) {
        try {
            final String content = Files.readString(Path.of(keyPath));
            if (content.contains("ENCRYPTED")) {
                final Console console = System.console();
                if (console != null) {
                    System.out.print("Chave criptografada detectada. Digite a senha: ");
                    return console.readPassword();
                }
            }
        } catch (Exception ignored) {
            // Ignora erros de leitura aqui, serão tratados depois
        }
        return null;
    }

    /**
     * Verifica se algum dos aliases da flag está presente nos argumentos.
     *
     * <p>Visibilidade de pacote para teste unitário direto (issue #986).</p>
     *
     * @param args argumentos da linha de comando
     * @param flags aliases aceitos (ex.: {@code --quiet}, {@code -q})
     * @return {@code true} se algum alias estiver presente
     */
    static boolean hasFlag(final String[] args, final String... flags) {
        for (final String arg : args) {
            for (final String flag : flags) {
                if (arg.equals(flag)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Imprime o banner de abertura da ferramenta.
     *
     * <p>Visibilidade de pacote para teste unitário direto (issue #986).</p>
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
     *
     * <p>Visibilidade de pacote para teste unitário direto (issue #986).</p>
     */
    static void printUsage() {
        System.out.println("Verifica se o credenciamento foi aprovado obtendo um token de acesso.");
        System.out.println();
        System.out.println(ANSI_BOLD + "USO:" + ANSI_RESET);
        System.out.println("  java -jar hubsaude-cliente-cli-X.Y.Z.jar \\");
        System.out.println("      --client-id=<ID> --key=<CHAVE.PEM> --cert=<CERT.PEM> [opções]");
        System.out.println();
        System.out.println(ANSI_BOLD + "PARÂMETROS OBRIGATÓRIOS:" + ANSI_RESET);
        System.out.println("  --client-id=<ID>      ID do cliente (fornecido no credenciamento)");
        System.out.println("  --key=<PATH>          Caminho para a chave privada PEM");
        System.out.println("  --cert=<PATH>         Caminho para o certificado PEM");
        System.out.println();
        System.out.println(ANSI_BOLD + "PARÂMETROS OPCIONAIS:" + ANSI_RESET);
        System.out.println("  --endpoint=<URL>      URL do token endpoint (desabilita descoberta)");
        System.out.println("  --fhir-base=<URL>     URL base FHIR (padrão: " + DEFAULT_FHIR_BASE + ")");
        System.out.println("  --scope=<SCOPE>       Scope a solicitar (padrão: " + DEFAULT_SCOPE + ")");
        System.out.println("  --password=<SENHA>    Senha da chave privada (se criptografada)");
        System.out.println("  --tls=<VERSAO>        Protocolo TLS: TLSv1.3 ou TLSv1.2 (padrão: " + DEFAULT_TLS + ")");
        System.out.println("  --alg=<ALG>           Algoritmo JWT: RS256, RS384, RS512, PS256, etc. (padrão: "
                + DEFAULT_ALG + ")");
        System.out.println(
                "  --trust=<PATH>        Certificado PEM do servidor " + "(trust anchor, para simulador/homologação)");
        System.out.println("  --quiet, -q           Oculta os detalhes do token (exibidos por padrão)");
        System.out.println("  --help, -h            Exibe esta mensagem");
        System.out.println();
        System.out.println(ANSI_BOLD + "EXEMPLOS:" + ANSI_RESET);
        System.out.println();
        System.out.println("  # Verificação básica (descoberta automática de endpoint)");
        System.out.println("  java -jar hubsaude-cliente-cli-0.0.0-SNAPSHOT.jar \\");
        System.out.println("      --client-id=hs-12345678 \\");
        System.out.println("      --key=minha-chave.pem \\");
        System.out.println("      --cert=meu-certificado.pem");
        System.out.println();
        System.out.println("  # Com endpoint explícito, TLS 1.2 e algoritmo RS256");
        System.out.println("  java -jar hubsaude-cliente-cli-0.0.0-SNAPSHOT.jar \\");
        System.out.println("      --client-id=hs-12345678 \\");
        System.out.println("      --key=minha-chave.pem \\");
        System.out.println("      --cert=meu-certificado.pem \\");
        System.out.println("      --endpoint=https://fhir.saude.go.gov.br/auth/token \\");
        System.out.println("      --alg=RS256 \\");
        System.out.println("      --tls=TLSv1.2 \\");
        System.out.println("      --quiet");
        System.out.println();
        System.out.println(ANSI_BOLD + "CÓDIGOS DE SAÍDA:" + ANSI_RESET);
        System.out.println("  0  Sucesso - token obtido, credenciamento OK");
        System.out.println("  1  Falha - erro de configuração ou acesso negado");
        System.out.println();
    }

    /**
     * Imprime mensagem informativa com o prefixo {@code [i]}.
     *
     * <p>Visibilidade de pacote para teste unitário direto (issue #986).</p>
     *
     * @param message mensagem a exibir
     */
    static void printInfo(final String message) {
        System.out.println(ANSI_YELLOW + "[i] " + message + ANSI_RESET);
    }

    /**
     * Imprime mensagem de sucesso com o prefixo {@code [OK]}.
     *
     * <p>Visibilidade de pacote para teste unitário direto (issue #986).</p>
     *
     * @param message mensagem a exibir
     */
    static void printSuccess(final String message) {
        System.out.println(ANSI_GREEN + "[OK] " + message + ANSI_RESET);
    }

    /**
     * Imprime mensagem de erro em {@code stderr} com o prefixo {@code [X]}.
     *
     * <p>Visibilidade de pacote para teste unitário direto (issue #986).</p>
     *
     * @param message mensagem a exibir
     */
    static void printError(final String message) {
        System.err.println(ANSI_RED + "[X] " + message + ANSI_RESET);
    }

    /**
     * Configuração parseada dos argumentos de linha de comando.
     */
    @SuppressWarnings({"checkstyle:VisibilityModifier", "PMD.DataClass"})
    // package-private fields acessados apenas neste CLI e em seus testes
    static final class Config {
        String clientId;
        String keyPath;
        String certPath;
        String tokenEndpoint;
        String fhirBase = DEFAULT_FHIR_BASE;
        String scope = DEFAULT_SCOPE;
        char[] keyPassword;
        String tlsProtocol = DEFAULT_TLS;
        String jwtAlgorithm = DEFAULT_ALG;
        String trustAnchorPath;
        boolean quiet;
    }
}
