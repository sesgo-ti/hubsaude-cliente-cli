/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2025-2026 Estado de Goiás (SES-GO) e Universidade Federal de Goiás (UFG).
 */

package br.gov.go.saude.hubsaude.client.cli;

import java.util.LinkedHashMap;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Utilitário que formata Header/Payload de um JWT como tabela legível,
 * uma claim por linha, preservando a ordem de inserção e exibindo
 * valores aninhados como JSON compacto inalterado.
 *
 * <p>O parse delega ao Jackson ({@link ObjectMapper#readTree}), já
 * presente no fat JAR via {@code hubsaude-cliente-java} — o parser
 * manual anterior aceitava JSON malformado com leniência e podia
 * exibir claims deturpadas (#1819). Entradas que não sejam um objeto
 * JSON válido caem no fallback de linha única indentada.</p>
 */
final class JwtPayloadFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JwtPayloadFormatter() {
        // utilitário
    }

    /**
     * Formata o JSON dado como uma tabela alinhada, uma claim por linha.
     * Quando o JSON não puder ser interpretado como objeto, é devolvido
     * em uma única linha indentada (fallback seguro).
     *
     * @param json texto JSON a ser exibido
     * @param indent prefixo de indentação (ex.: "    ")
     * @return string multi-linha pronta para impressão
     */
    @SuppressFBWarnings(
            value = "FORMAT_STRING_MANIPULATION",
            justification = "Largura do formato deriva apenas de maxKeyLen "
                    + "(int interno calculado das chaves); não há entrada do "
                    + "usuário no template de formato.")
    static String formatAsTable(final String json, final String indent) {
        final Map<String, String> fields = parseTopLevelObject(json);
        if (fields.isEmpty()) {
            return indent + json;
        }

        final int maxKeyLen = computeMaxKeyLength(fields);
        final StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            out.append(String.format("%s%-" + maxKeyLen + "s : %s%n", indent, entry.getKey(), entry.getValue()));
        }
        return out.toString();
    }

    private static int computeMaxKeyLength(final Map<String, String> fields) {
        int max = 1;
        for (String key : fields.keySet()) {
            if (key.length() > max) {
                max = key.length();
            }
        }
        return max;
    }

    /**
     * Interpreta o texto como objeto JSON e devolve as claims de topo na
     * ordem do documento. Retorna mapa vazio para entrada nula, JSON
     * inválido ou raiz que não seja objeto (aciona o fallback).
     */
    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private static Map<String, String> parseTopLevelObject(final String json) {
        final Map<String, String> fields = new LinkedHashMap<>();
        if (json == null) {
            return fields;
        }
        final JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JacksonException e) {
            return fields;
        }
        if (root == null || !root.isObject()) {
            return fields;
        }
        for (Map.Entry<String, JsonNode> property : root.properties()) {
            fields.put(property.getKey(), renderValue(property.getValue()));
        }
        return fields;
    }

    /**
     * Strings são exibidas sem aspas e com escapes interpretados; os
     * demais tipos (número, booleano, null, objeto, array) mantêm a
     * forma JSON compacta.
     */
    private static String renderValue(final JsonNode value) {
        return value.isString() ? value.stringValue() : value.toString();
    }
}
