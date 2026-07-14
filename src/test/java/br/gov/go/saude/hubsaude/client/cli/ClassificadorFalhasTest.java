/*
 * Copyright (c) 2026 SES-GO / UFG
 * Todos os direitos reservados.
 */

package br.gov.go.saude.hubsaude.client.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.gov.go.saude.hubsaude.client.SigningException;
import br.gov.go.saude.hubsaude.client.SmartTokenException;

/**
 * Testes unitários de {@link ClassificadorFalhas}: mapeamento das exceções
 * do fluxo da CLI para a taxonomia de códigos de saída 0/3/4/6 do
 * {@code hubsaude-cli} (issue #817), incluindo o comportamento fail-safe —
 * falha ambígua jamais classifica como credencial não apta.
 */
class ClassificadorFalhasTest {

    // ------------------------------------------------------------------
    // Constantes espelham a taxonomia de hubsaude-cli/internal/exit/exit.go
    // ------------------------------------------------------------------

    @Test
    void taxonomiaEspelhaOsCodigosDoHubsaudeCli() {
        assertThat(ClassificadorFalhas.SUCESSO).isZero();
        assertThat(ClassificadorFalhas.USO).isEqualTo(3);
        assertThat(ClassificadorFalhas.INFRA).isEqualTo(4);
        assertThat(ClassificadorFalhas.CREDENCIAL_NAO_APTA).isEqualTo(6);
    }

    // ------------------------------------------------------------------
    // Erros de uso/configuração → 3
    // ------------------------------------------------------------------

    @Test
    void argumentoInvalidoClassificaComoUso() {
        assertThat(ClassificadorFalhas.classificar(new IllegalArgumentException("--ig é obrigatório")))
                .isEqualTo(ClassificadorFalhas.USO);
    }

    @Test
    void estadoInvalidoDoBuilderClassificaComoUso() {
        assertThat(ClassificadorFalhas.classificar(
                        new IllegalStateException("Defina tokenEndpoint OU fhirBase, não ambos")))
                .isEqualTo(ClassificadorFalhas.USO);
    }

    @Test
    void falhaDeAssinaturaClassificaComoUso() {
        // Chave incapaz de assinar com o algoritmo configurado: falha local
        // e determinística de configuração (fonte de chave não suportada)
        assertThat(ClassificadorFalhas.classificar(
                        new SigningException("Falha ao assinar dados com algoritmo RS384", new Exception())))
                .isEqualTo(ClassificadorFalhas.USO);
    }

    // ------------------------------------------------------------------
    // Credencial NÃO apta (falha determinística) → 6
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {400, 401})
    void rejeicaoHttpDoTokenEndpointClassificaComoCredencialNaoApta(final int status) {
        final SmartTokenException falha =
                new SmartTokenException("Falha ao obter token: HTTP " + status + " — {\"error\":\"invalid_client\"}");

        assertThat(ClassificadorFalhas.classificar(falha)).isEqualTo(ClassificadorFalhas.CREDENCIAL_NAO_APTA);
    }

    @Test
    void rejeicao401ComRetryAfterClassificaComoCredencialNaoApta() {
        final SmartTokenException falha = new SmartTokenException(
                "Falha ao obter token: HTTP 401 (Retry-After: 30) — {\"error\":\"invalid_client\"}");

        assertThat(ClassificadorFalhas.classificar(falha)).isEqualTo(ClassificadorFalhas.CREDENCIAL_NAO_APTA);
    }

    @Test
    void conexaoTlsAbortadaAposHandshakeMtlsClassificaComoCredencialNaoApta() {
        // Mensagem produzida por SmartTokenClient.retriableOrRethrow quando o
        // servidor aborta a conexão ao rejeitar o certificado de cliente
        final SmartTokenException falha = new SmartTokenException(
                "Conexão TLS abortada pelo servidor após o handshake mTLS contra https://auth.exemplo.gov.br/token."
                        + " Causa provável: certificado de cliente rejeitado"
                        + " (revogado, expirado ou não confiável).",
                new IOException());

        assertThat(ClassificadorFalhas.classificar(falha)).isEqualTo(ClassificadorFalhas.CREDENCIAL_NAO_APTA);
    }

    // ------------------------------------------------------------------
    // Infraestrutura de rede (teste inconclusivo) → 4
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 502, 503})
    void rateLimitECincoXxDoTokenEndpointClassificamComoInfra(final int status) {
        final SmartTokenException falha =
                new SmartTokenException("Falha ao obter token: HTTP " + status + " — <empty>");

        assertThat(ClassificadorFalhas.classificar(falha)).isEqualTo(ClassificadorFalhas.INFRA);
    }

    @Test
    void falhaDeRedeClassificaComoInfra() {
        assertThat(ClassificadorFalhas.classificar(new IOException("connection refused")))
                .isEqualTo(ClassificadorFalhas.INFRA);
        assertThat(ClassificadorFalhas.classificar(new HttpTimeoutException("request timed out")))
                .isEqualTo(ClassificadorFalhas.INFRA);
    }

    @Test
    void falhaDeDiscoveryClassificaComoInfra() {
        assertThat(ClassificadorFalhas.classificar(
                        new SmartTokenException("Falha ao obter smart-configuration (404): <empty>")))
                .isEqualTo(ClassificadorFalhas.INFRA);
    }

    @Test
    void tentativasEsgotadasClassificamComoInfra() {
        assertThat(ClassificadorFalhas.classificar(new SmartTokenException(
                        "Falha após 3 tentativas: connection refused", new IOException("connection refused"))))
                .isEqualTo(ClassificadorFalhas.INFRA);
    }

    @Test
    void interrupcaoClassificaComoInfra() {
        assertThat(ClassificadorFalhas.classificar(new InterruptedException("interrompida")))
                .isEqualTo(ClassificadorFalhas.INFRA);
    }

    // ------------------------------------------------------------------
    // Fail-safe: ambíguo NUNCA classifica como credencial não apta
    // ------------------------------------------------------------------

    @Test
    void falhaAmbiguaClassificaComoInfraNuncaComoCredencialNaoApta() {
        assertThat(ClassificadorFalhas.classificar(new SmartTokenException("Resposta não contém 'access_token'")))
                .isEqualTo(ClassificadorFalhas.INFRA);
        assertThat(ClassificadorFalhas.classificar(new SmartTokenException((String) null)))
                .isEqualTo(ClassificadorFalhas.INFRA);
        assertThat(ClassificadorFalhas.classificar(new RuntimeException("falha inesperada")))
                .isEqualTo(ClassificadorFalhas.INFRA);
    }

    @Test
    void statusHttpForaDoInicioDaMensagemNaoClassificaComoCredencialNaoApta() {
        // O padrão exige o prefixo exato da mensagem da biblioteca; um corpo
        // de erro que apenas mencione "HTTP 401" não é evidência determinística
        final SmartTokenException falha =
                new SmartTokenException("Falha ao obter smart-configuration (502): upstream retornou HTTP 401");

        assertThat(ClassificadorFalhas.classificar(falha)).isEqualTo(ClassificadorFalhas.INFRA);
    }

    // ------------------------------------------------------------------
    // Status HTTP malformado na mensagem (entrada externa) — issue #1407
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"4o1", "abc", "40", "", "-401", " 401"})
    void statusHttpMalformadoNaoLancaExcecaoEClassificaComoInfra(final String status) {
        // Mensagem de resposta externa com status não numérico ou fora do
        // formato de três dígitos: jamais NumberFormatException; fail-safe
        final SmartTokenException falha = new SmartTokenException("Falha ao obter token: HTTP " + status + " — corpo");

        assertThatCode(() -> ClassificadorFalhas.classificar(falha)).doesNotThrowAnyException();
        assertThat(ClassificadorFalhas.classificar(falha)).isEqualTo(ClassificadorFalhas.INFRA);
    }

    @Test
    void statusHttpComDigitosExtrasNaoClassificaComoCredencialNaoApta() {
        // "4013" não é um status de três dígitos; o prefixo "401" não deve
        // ser interpretado como rejeição determinística da credencial
        final SmartTokenException falha = new SmartTokenException("Falha ao obter token: HTTP 4013 — corpo");

        assertThat(ClassificadorFalhas.classificar(falha)).isEqualTo(ClassificadorFalhas.INFRA);
    }
}
