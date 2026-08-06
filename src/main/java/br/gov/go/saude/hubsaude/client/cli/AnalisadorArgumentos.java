/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2025-2026 Estado de Goiás (SES-GO) e Universidade Federal de Goiás (UFG).
 */

package br.gov.go.saude.hubsaude.client.cli;

import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Interpreta e valida os argumentos de linha de comando do
 * {@link VerificarAcesso}, produzindo a {@link Config} com os valores
 * padrão aplicados.
 *
 * <p>Colaborador extraído de {@code VerificarAcesso} para
 * concentrar o parsing das flags, a validação dos parâmetros obrigatórios
 * (incluindo o contexto {@code hub_ctx}, concern
 * client-assertion-contexto-ig.md §3.4) e a leitura interativa de senha da
 * chave privada quando necessária.</p>
 */
final class AnalisadorArgumentos {

    /** URL base FHIR padrão usada na descoberta do token endpoint. */
    static final String DEFAULT_FHIR_BASE = "https://hub.saude.go.gov.br";

    /** Scope solicitado por padrão. */
    static final String DEFAULT_SCOPE = "system/Patient.rs";

    /** Protocolo TLS padrão. */
    static final String DEFAULT_TLS = "TLSv1.3";

    /**
     * Algoritmo JWT padrão (RS384 — concern client-assertion-contexto-ig.md
     * §3.2: o Servidor de Autorização aceita apenas RS384 e ES384).
     */
    static final String DEFAULT_ALG = "RS384";

    /**
     * Formato do alias de IG em {@code --ig} (concern
     * client-assertion-contexto-ig.md §3.4).
     */
    private static final Pattern IG_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,30}$");

    /**
     * Formato SemVer completo (MAJOR.MINOR.PATCH, sem pre-release/build) em
     * {@code --ig-versao} (concern client-assertion-contexto-ig.md §3.4).
     */
    private static final Pattern IG_VERSAO_PATTERN = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

    /**
     * Tabela de opções {@code --prefixo=valor} e seus setters: substitui a
     * antiga cadeia if/else-if (CognitiveComplexity 17) por dispatch
     * declarativo — os prefixos são disjuntos entre si, então a ordem de
     * iteração é irrelevante.
     */
    private static final Map<String, BiConsumer<Config, String>> OPCOES_COM_VALOR = Map.ofEntries(
            Map.entry("--client-id=", (config, valor) -> {
                config.clientId = valor;
            }),
            Map.entry("--key=", (config, valor) -> {
                config.keyPath = valor;
            }),
            Map.entry("--cert=", (config, valor) -> {
                config.certPath = valor;
            }),
            Map.entry("--ig=", (config, valor) -> {
                config.ig = valor;
            }),
            Map.entry("--ig-versao=", (config, valor) -> {
                config.igVersao = valor;
            }),
            Map.entry("--endpoint=", (config, valor) -> {
                config.tokenEndpoint = valor;
            }),
            Map.entry("--fhir-base=", (config, valor) -> {
                config.fhirBase = valor;
            }),
            Map.entry("--scope=", (config, valor) -> {
                config.scope = valor;
            }),
            Map.entry("--password=", (config, valor) -> {
                config.keyPassword = valor.toCharArray();
            }),
            Map.entry("--tls=", (config, valor) -> {
                config.tlsProtocol = valor;
            }),
            Map.entry("--alg=", (config, valor) -> {
                config.jwtAlgorithm = valor.toUpperCase(Locale.ROOT);
            }),
            Map.entry("--kid=", (config, valor) -> {
                config.keyId = valor;
            }),
            Map.entry("--trust=", (config, valor) -> {
                config.trustAnchorPath = valor;
            }));

    private AnalisadorArgumentos() {
        // Utilitário não instanciável
    }

    /**
     * Interpreta os argumentos de linha de comando e monta a configuração,
     * aplicando os valores padrão e validando os parâmetros obrigatórios.
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

    private static void parseArgument(final String arg, final Config config) {
        for (final Map.Entry<String, BiConsumer<Config, String>> opcao : OPCOES_COM_VALOR.entrySet()) {
            if (arg.startsWith(opcao.getKey())) {
                opcao.getValue().accept(config, extractValue(arg, opcao.getKey()));
                return;
            }
        }
        if ("--quiet".equals(arg) || "-q".equals(arg)) {
            config.quiet = true;
        } else if ("--show-token".equals(arg)) {
            config.showToken = true;
        } else if (!"--help".equals(arg) && !"-h".equals(arg) && !"--debug".equals(arg)) {
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
        validateHubContextArgs(config);
    }

    /**
     * Valida os parâmetros do claim {@code hub_ctx} (concern
     * client-assertion-contexto-ig.md §3.4): {@code --ig} e
     * {@code --ig-versao} são obrigatórios e devem seguir, respectivamente,
     * o formato de alias de IG e SemVer completo MAJOR.MINOR.PATCH.
     *
     * @param config configuração parseada
     * @throws IllegalArgumentException se ausentes ou fora do formato
     */
    private static void validateHubContextArgs(final Config config) {
        if (config.ig == null) {
            throw new IllegalArgumentException("--ig é obrigatório (alias do Guia de Implementação)");
        }
        if (!IG_PATTERN.matcher(config.ig).matches()) {
            throw new IllegalArgumentException(
                    "--ig inválido: '" + config.ig + "' (use minúsculas, dígitos e hífen, iniciando por letra)");
        }
        if (config.igVersao == null) {
            throw new IllegalArgumentException("--ig-versao é obrigatório (versão SemVer do Guia de Implementação)");
        }
        if (!IG_VERSAO_PATTERN.matcher(config.igVersao).matches()) {
            throw new IllegalArgumentException("--ig-versao inválido: '" + config.igVersao
                    + "' (use SemVer completo MAJOR.MINOR.PATCH, ex.: 0.0.1)");
        }
    }

    /**
     * Tenta detectar se a chave é criptografada e solicita senha se necessário.
     *
     * @param keyPath caminho da chave
     * @return senha digitada ou null se não necessária/não disponível
     */
    @SuppressWarnings({"PMD.ReturnEmptyCollectionRatherThanNull", "PMD.SystemPrintln"})
    // null = sem senha, não senha vazia; prompt interativo no console é a UI desta CLI
    @SuppressFBWarnings(
            value = {"PZLA_PREFER_ZERO_LENGTH_ARRAYS", "REC_CATCH_EXCEPTION"},
            justification = "null representa 'sem senha' (semântica distinta de "
                    + "senha vazia char[0]); o catch genérico ignora falhas de "
                    + "leitura, tratadas adiante na validação dos arquivos.")
    private static char @Nullable [] promptPasswordIfNeeded(final String keyPath) {
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
     * Valida que o arquivo existe e é legível.
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
     * Configuração parseada dos argumentos de linha de comando.
     */
    @SuppressWarnings({"checkstyle:VisibilityModifier", "PMD.DataClass"})
    // package-private fields acessados apenas neste CLI e em seus testes
    static final class Config {
        @Nullable
        String clientId;

        @Nullable
        String keyPath;

        @Nullable
        String certPath;

        @Nullable
        String ig;

        @Nullable
        String igVersao;

        @Nullable
        String tokenEndpoint;

        String fhirBase = DEFAULT_FHIR_BASE;
        String scope = DEFAULT_SCOPE;
        char @Nullable [] keyPassword;
        String tlsProtocol = DEFAULT_TLS;
        String jwtAlgorithm = DEFAULT_ALG;

        @Nullable
        String keyId;

        @Nullable
        String trustAnchorPath;

        boolean quiet;

        boolean showToken;
    }
}
