/*
 * Copyright (c) 2026 SES-GO / UFG
 * Todos os direitos reservados.
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
 */
package br.gov.go.saude.hubsaude.client.cli;
