/*
 * Copyright (c) 2026 SES-GO / UFG
 * Todos os direitos reservados.
 */

package br.gov.go.saude.hubsaude.client.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes do contrato de erro de {@link VerificarAcesso}: mensagem curta e
 * acionável no stderr por padrão, sem stack trace; detalhes técnicos apenas
 * em modo diagnóstico ({@code --debug}).
 */
class VerificarAcessoFalhaTest {

    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    private PrintStream originalErr;

    @BeforeEach
    void capturarStderr() {
        originalErr = System.err;
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restaurarStderr() {
        System.setErr(originalErr);
    }

    @Test
    void semDebugEmiteMensagemCurtaSemStackTrace() {
        VerificarAcesso.reportarFalha(new RuntimeException("endpoint inacessível"), false);

        final String saida = stderr.toString(StandardCharsets.UTF_8);
        assertThat(saida)
                .contains("Falha ao obter token: endpoint inacessível")
                .contains("--debug")
                .doesNotContain("\tat ")
                .doesNotContain("java.lang.RuntimeException");
    }

    @Test
    void comDebugEmiteDetalhesTecnicosViaLogger() {
        VerificarAcesso.reportarFalha(new RuntimeException("endpoint inacessível"), true);

        final String saida = stderr.toString(StandardCharsets.UTF_8);
        assertThat(saida)
                .contains("Falha ao obter token: endpoint inacessível")
                .contains("java.lang.RuntimeException")
                .contains("\tat ");
    }
}
