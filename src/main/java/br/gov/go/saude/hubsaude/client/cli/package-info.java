/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2025-2026 Estado de Goiás (SES-GO) e Universidade Federal de Goiás (UFG).
 */

/**
 * Ferramenta de linha de comando {@code verificar-acesso}: diagnóstico
 * pós-credenciamento do acesso de um estabelecimento ao HubSaúde.
 *
 * <p>O fluxo verificado é o do SMART Backend Services: obtenção de
 * access token no authorization server (via {@code hubsaude-cliente-java})
 * seguida de chamada FHIR autenticada ao Gateway. Cada colaborador cobre
 * uma etapa da experiência de console: {@code AnalisadorArgumentos}
 * (parsing e validação dos argumentos), {@code ClienteToken} (costura
 * testável sobre o {@code SmartTokenClient}), {@code ClassificadorFalhas}
 * (mapeamento de falhas em códigos de saída estáveis, contrato para
 * automação), {@code JwtPayloadFormatter} e {@code SaidaConsole}
 * (apresentação legível do resultado ao operador do estabelecimento).</p>
 *
 * <h2>Null-safety (REC-09/ADR-67)</h2>
 *
 * <p>Pacote anotado com {@link org.jspecify.annotations.NullMarked}:
 * todo tipo é <strong>não-nulo por padrão</strong>; exceções são
 * marcadas explicitamente com
 * {@link org.jspecify.annotations.Nullable}.</p>
 */
@NullMarked
package br.gov.go.saude.hubsaude.client.cli;

import org.jspecify.annotations.NullMarked;
