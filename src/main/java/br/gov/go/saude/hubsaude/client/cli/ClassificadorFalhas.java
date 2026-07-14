/*
 * Copyright (c) 2026 SES-GO / UFG
 * Todos os direitos reservados.
 */

package br.gov.go.saude.hubsaude.client.cli;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.gov.go.saude.hubsaude.client.SigningException;
import br.gov.go.saude.hubsaude.client.SmartTokenException;

/**
 * Classifica as falhas do {@link VerificarAcesso} nos códigos de saída da
 * taxonomia do {@code hubsaude-cli} (issue #817), espelhando a semântica de
 * {@code hubsaude-cli/internal/exit/exit.go} (spec §12.1) para o caso de uso
 * {@code auth verify}:
 *
 * <ul>
 *   <li>{@link #SUCESSO} (0) — credencial apta: token obtido;</li>
 *   <li>{@link #USO} (3) — erro de uso/configuração: argumentos ou arquivos
 *       inválidos, fonte de chave não suportada;</li>
 *   <li>{@link #INFRA} (4) — falha de infraestrutura de rede (conexão,
 *       timeout, descoberta {@code .well-known}, rate limit 429 e 5xx do
 *       token endpoint) — teste <em>inconclusivo</em>;</li>
 *   <li>{@link #CREDENCIAL_NAO_APTA} (6) — credencial NÃO apta: rejeição
 *       determinística do servidor de autorização (HTTP 400/401
 *       {@code invalid_client}) ou do handshake mTLS.</li>
 * </ul>
 *
 * <p><strong>Fail-safe:</strong> enquanto o {@code hubsaude-cliente-java}
 * não expõe exceções tipadas por causa (evolução prevista na issue #1032),
 * a distinção do código 6 apoia-se nos formatos de mensagem estáveis
 * produzidos pela biblioteca ({@code buildHttpErrorMessage} e o diagnóstico
 * de rejeição do certificado de cliente no mTLS). Toda falha que não puder
 * ser atribuída com certeza determinística à credencial classifica como
 * {@link #INFRA} — a ferramenta jamais declara uma credencial inapta por
 * uma falha ambígua.</p>
 */
final class ClassificadorFalhas {

    /** Sucesso — token obtido, credencial apta (espelha {@code exit.OK}). */
    static final int SUCESSO = 0;

    /**
     * Erro de uso/configuração — argumentos, arquivos ou fonte de chave
     * inválidos (espelha {@code exit.Usage}).
     */
    static final int USO = 3;

    /**
     * Falha de infraestrutura de rede — teste inconclusivo; inclui
     * descoberta, rate limit 429 e 5xx do token endpoint (espelha
     * {@code exit.Infra}).
     */
    static final int INFRA = 4;

    /**
     * Credencial não apta — rejeição determinística: HTTP 400/401
     * ({@code invalid_client}) ou handshake mTLS (espelha
     * {@code exit.CredentialBad}).
     */
    static final int CREDENCIAL_NAO_APTA = 6;

    /** Status HTTP 400 (Bad Request) do token endpoint, como texto. */
    private static final String HTTP_BAD_REQUEST = "400";

    /** Status HTTP 401 (Unauthorized) do token endpoint, como texto. */
    private static final String HTTP_UNAUTHORIZED = "401";

    /**
     * Formato fixo da mensagem produzida pela biblioteca para resposta
     * HTTP ≠ 200 do token endpoint ({@code SmartTokenClient.buildHttpErrorMessage}).
     * Apenas o token endpoint gera esse prefixo; a descoberta
     * {@code .well-known} usa outro texto e permanece classificada como infra.
     *
     * <p>O grupo captura exatamente três dígitos — o lookahead negativo
     * rejeita dígitos extras (ex.: {@code HTTP 4013}) — e é comparado
     * textualmente com {@link #HTTP_BAD_REQUEST}/{@link #HTTP_UNAUTHORIZED},
     * sem conversão numérica da mensagem (entrada externa): elimina a
     * {@link NumberFormatException} por construção (CodeQL
     * {@code java/uncaught-number-format-exception}, issue #1407).</p>
     */
    private static final Pattern HTTP_TOKEN_ENDPOINT = Pattern.compile("^Falha ao obter token: HTTP (\\d{3})(?!\\d)");

    /**
     * Prefixo fixo do diagnóstico da biblioteca para conexão TLS abortada
     * pelo servidor após o handshake mTLS (certificado de cliente rejeitado)
     * — {@code SmartTokenClient.retriableOrRethrow}.
     */
    private static final String PREFIXO_MTLS_ABORTADO = "Conexão TLS abortada pelo servidor após o handshake mTLS";

    private ClassificadorFalhas() {
        // Utilitário não instanciável
    }

    /**
     * Classifica a falha no código de saída correspondente da taxonomia.
     *
     * <p>Erros de pré-condição local ({@link IllegalArgumentException},
     * {@link IllegalStateException} do builder e {@link SigningException} —
     * chave incapaz de assinar, falha local e determinística de
     * configuração) classificam como {@link #USO}. As
     * {@link SmartTokenException} são inspecionadas por
     * {@code classificarSmartToken}; as demais ({@link java.io.IOException},
     * {@link InterruptedException} e exceções não previstas) classificam
     * como {@link #INFRA} (fail-safe).</p>
     *
     * @param falha falha capturada pelo fluxo da CLI
     * @return código de saída ({@link #USO}, {@link #INFRA} ou
     *         {@link #CREDENCIAL_NAO_APTA})
     */
    static int classificar(final Exception falha) {
        if (falha instanceof IllegalArgumentException
                || falha instanceof IllegalStateException
                || falha instanceof SigningException) {
            return USO;
        }
        if (falha instanceof SmartTokenException) {
            return classificarSmartToken(falha.getMessage());
        }
        return INFRA;
    }

    /**
     * Classifica uma {@link SmartTokenException} pela mensagem: HTTP 400/401
     * do token endpoint e rejeição mTLS são falhas determinísticas de
     * credencial ({@link #CREDENCIAL_NAO_APTA}); HTTP 429/5xx e qualquer
     * outra mensagem (descoberta, parsing de PEM, tentativas esgotadas)
     * classificam como {@link #INFRA}.
     *
     * <p>A comparação do status é textual — nunca lança
     * {@link NumberFormatException} para mensagem malformada; status que
     * não case exatamente com três dígitos degrada fail-safe para
     * {@link #INFRA} (issue #1407).</p>
     *
     * @param mensagem mensagem da exceção (pode ser {@code null})
     * @return código de saída
     */
    private static int classificarSmartToken(final String mensagem) {
        if (mensagem == null) {
            return INFRA;
        }
        if (mensagem.startsWith(PREFIXO_MTLS_ABORTADO)) {
            return CREDENCIAL_NAO_APTA;
        }
        final Matcher matcher = HTTP_TOKEN_ENDPOINT.matcher(mensagem);
        if (matcher.find()) {
            final String status = matcher.group(1);
            if (HTTP_BAD_REQUEST.equals(status) || HTTP_UNAUTHORIZED.equals(status)) {
                return CREDENCIAL_NAO_APTA;
            }
        }
        return INFRA;
    }
}
