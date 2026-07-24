/*
 * Copyright (c) 2026 SES-GO / UFG
 * Todos os direitos reservados.
 */

package br.gov.go.saude.hubsaude.client.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import br.gov.go.saude.hubsaude.client.SmartTokenClient;
import br.gov.go.saude.hubsaude.client.SmartTokenClientBuilder;

/**
 * Costura testável sobre a chamada ao {@link SmartTokenClient}
 * (issue #1034): expõe ao fluxo do {@link VerificarAcesso} apenas o que a
 * CLI consome — algoritmo JWT efetivo, token endpoint efetivo e a obtenção
 * da resposta de token — permitindo substituir a implementação real por um
 * dublê em testes unitários (o {@code SmartTokenClient} é {@code final} e
 * exige rede/PEMs reais).
 *
 * <p>A montagem do cliente real, com os mesmos parâmetros e a mesma ordem
 * de validação da implementação original, fica em
 * {@link #conectar(AnalisadorArgumentos.Config)}.</p>
 */
final class ClienteToken {

    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 60;

    private final String jwtAlgorithm;
    private final String tokenEndpoint;
    private final Operacao operacao;

    ClienteToken(final String jwtAlgorithm, final String tokenEndpoint, final Operacao operacao) {
        this.jwtAlgorithm = jwtAlgorithm;
        this.tokenEndpoint = tokenEndpoint;
        this.operacao = operacao;
    }

    /**
     * Monta o {@link SmartTokenClient} real a partir da configuração,
     * preservando os parâmetros, timeouts e a validação de arquivos da
     * implementação original de {@code VerificarAcesso}.
     *
     * @param config configuração parseada
     * @return costura sobre o cliente real
     * @throws IOException se os arquivos PEM não puderem ser lidos ou a
     *         descoberta do endpoint falhar
     * @throws IllegalArgumentException se algum arquivo configurado não
     *         existir ou não puder ser lido
     */
    static ClienteToken conectar(final AnalisadorArgumentos.Config config) throws IOException {
        final SmartTokenClientBuilder builder = SmartTokenClient.builder()
                .clientId(Objects.requireNonNull(
                        config.clientId, "clientId obrigatório — garantido por AnalisadorArgumentos.validar()"))
                .privateKeyPem(Path.of(config.keyPath))
                .certificatePem(Path.of(config.certPath))
                .tlsProtocol(config.tlsProtocol)
                .jwtAlgorithm(config.jwtAlgorithm)
                .hubContext(
                        Objects.requireNonNull(
                                config.ig, "ig obrigatório — garantido por AnalisadorArgumentos.validar()"),
                        Objects.requireNonNull(
                                config.igVersao, "igVersao obrigatório — garantido por AnalisadorArgumentos.validar()"))
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .requestTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS));

        if (config.keyId != null) {
            builder.keyId(config.keyId);
        }

        if (config.tokenEndpoint == null) {
            builder.fhirBase(config.fhirBase);
        } else {
            builder.tokenEndpoint(config.tokenEndpoint);
        }

        if (config.keyPassword != null) {
            builder.privateKeyPassword(config.keyPassword);
        }

        if (config.trustAnchorPath != null) {
            AnalisadorArgumentos.validateFile(config.trustAnchorPath, "Trust anchor");
            builder.serverTrustAnchor(Path.of(config.trustAnchorPath));
        }

        final SmartTokenClient client = builder.build();
        return new ClienteToken(client.getJwtAlgorithm(), client.getTokenEndpoint(), client::obtainTokenResponse);
    }

    /**
     * Algoritmo JWT efetivo do cliente.
     *
     * @return algoritmo JWT (ex.: RS384)
     */
    String jwtAlgorithm() {
        return jwtAlgorithm;
    }

    /**
     * Token endpoint efetivo do cliente (explícito ou descoberto).
     *
     * @return URL do token endpoint
     */
    String tokenEndpoint() {
        return tokenEndpoint;
    }

    /**
     * Obtém a resposta de token para o scope dado.
     *
     * @param scope scope a solicitar
     * @return resposta de token do servidor de autorização
     * @throws IOException em caso de erro de rede ou de protocolo
     * @throws InterruptedException se a thread for interrompida
     */
    SmartTokenClient.TokenResponse obterToken(final String scope) throws IOException, InterruptedException {
        return operacao.obter(scope);
    }

    /**
     * Fábrica do cliente usada pelo fluxo da CLI; em produção é
     * {@link ClienteToken#conectar(AnalisadorArgumentos.Config)}, em testes
     * pode devolver um {@link ClienteToken} com {@link Operacao} fake.
     */
    @FunctionalInterface
    interface Fabrica {

        /**
         * Cria o cliente de token a partir da configuração da CLI.
         *
         * @param config configuração parseada
         * @return cliente pronto para obter tokens
         * @throws IOException se os arquivos PEM não puderem ser lidos ou a
         *         descoberta do endpoint falhar
         */
        ClienteToken criar(AnalisadorArgumentos.Config config) throws IOException;
    }

    /**
     * Obtenção da resposta de token propriamente dita (a chamada de rede no
     * cliente real).
     */
    @FunctionalInterface
    interface Operacao {

        /**
         * Obtém a resposta de token para o scope dado.
         *
         * @param scope scope a solicitar
         * @return resposta de token do servidor de autorização
         * @throws IOException em caso de erro de rede ou de protocolo
         * @throws InterruptedException se a thread for interrompida
         */
        SmartTokenClient.TokenResponse obter(String scope) throws IOException, InterruptedException;
    }
}
