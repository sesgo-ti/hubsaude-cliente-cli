/*
 * Copyright (c) 2026 SES-GO / UFG
 * Todos os direitos reservados.
 */

package br.gov.go.saude.hubsaude.client.cli;

import java.util.LinkedHashMap;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Utilitário que formata Header/Payload de um JWT como tabela legível,
 * uma claim por linha. Faz parse mínimo de um objeto JSON plano,
 * preservando ordem de inserção e tratando valores aninhados como
 * JSON compacto inalterado.
 *
 * <p>Esta classe é intencionalmente standalone e sem dependências
 * externas: o objetivo é evitar arrastar bibliotecas JSON no fat JAR
 * do CLI quando o uso é restrito a inspeção visual do token.</p>
 */
final class JwtPayloadFormatter {

    private static final int UNICODE_HEX_START_OFFSET = 2;
    private static final int UNICODE_HEX_END_OFFSET = 6;
    private static final int UNICODE_ESCAPE_TOTAL = 5;
    private static final int UNICODE_ESCAPE_CONSUMED = 6;
    private static final int SIMPLE_ESCAPE_CONSUMED = 2;
    private static final int HEX_RADIX = 16;
    private static final int MIN_OBJECT_LENGTH = 2;

    private JwtPayloadFormatter() {
        // utilitário
    }

    /**
     * Formata o JSON dado como uma tabela alinhada, uma claim por linha.
     * Quando o JSON não puder ser interpretado, é devolvido em uma única
     * linha indentada (fallback seguro).
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
        int max = 0;
        for (String key : fields.keySet()) {
            if (key.length() > max) {
                max = key.length();
            }
        }
        return max;
    }

    /**
     * Parse mínimo de um objeto JSON plano. Retorna mapa vazio se o
     * JSON não representar um objeto válido.
     */
    private static Map<String, String> parseTopLevelObject(final String json) {
        if (json == null) {
            return new LinkedHashMap<>();
        }

        final String trimmed = json.trim();
        if (trimmed.length() < MIN_OBJECT_LENGTH
                || trimmed.charAt(0) != '{'
                || trimmed.charAt(trimmed.length() - 1) != '}') {
            return new LinkedHashMap<>();
        }

        try {
            return parseObjectFields(trimmed);
        } catch (IllegalArgumentException ignored) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private static Map<String, String> parseObjectFields(final String text) {
        final Map<String, String> result = new LinkedHashMap<>();
        final int end = text.length() - 1;
        int pos = 1;
        while (pos < end) {
            pos = skipWhitespace(text, pos, end);
            if (pos >= end) {
                break;
            }
            requireChar(text, pos, '"');
            final int keyEnd = findStringEnd(text, pos);
            final String key = unescape(text.substring(pos + 1, keyEnd));
            pos = skipWhitespace(text, keyEnd + 1, end);
            requireChar(text, pos, ':');
            pos = skipWhitespace(text, pos + 1, end);
            final int valueEnd = findValueEnd(text, pos);
            result.put(key, renderValue(text.substring(pos, valueEnd).trim()));
            pos = skipWhitespace(text, valueEnd, end);
            if (pos < end && text.charAt(pos) == ',') {
                pos++;
            }
        }
        return result;
    }

    private static void requireChar(final String text, final int pos, final char expected) {
        if (pos >= text.length() || text.charAt(pos) != expected) {
            throw new IllegalArgumentException("esperado '" + expected + "' em " + pos);
        }
    }

    private static int skipWhitespace(final String text, final int from, final int end) {
        int pos = from;
        while (pos < end && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    private static int findStringEnd(final String text, final int openQuote) {
        int pos = openQuote + 1;
        while (pos < text.length()) {
            final char current = text.charAt(pos);
            if (current == '\\') {
                pos += 2;
                continue;
            }
            if (current == '"') {
                return pos;
            }
            pos++;
        }
        throw new IllegalArgumentException("string não terminada");
    }

    private static int findValueEnd(final String text, final int from) {
        final char first = text.charAt(from);
        if (first == '"') {
            return findStringEnd(text, from) + 1;
        }
        if (first == '{' || first == '[') {
            return findStructuredEnd(text, from);
        }
        return findScalarEnd(text, from);
    }

    private static int findStructuredEnd(final String text, final int from) {
        final char open = text.charAt(from);
        final char close = (open == '{') ? '}' : ']';
        int depth = 0;
        int pos = from;
        while (pos < text.length()) {
            final char current = text.charAt(pos);
            if (current == '"') {
                pos = findStringEnd(text, pos) + 1;
                continue;
            }
            if (current == open) {
                depth++;
            } else if (current == close) {
                depth--;
                if (depth == 0) {
                    return pos + 1;
                }
            }
            pos++;
        }
        throw new IllegalArgumentException("estrutura não terminada");
    }

    private static int findScalarEnd(final String text, final int from) {
        int pos = from;
        while (pos < text.length() && !isScalarTerminator(text.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    private static boolean isScalarTerminator(final char character) {
        return character == ',' || character == '}' || character == ']' || Character.isWhitespace(character);
    }

    private static String renderValue(final String raw) {
        if (raw.length() >= MIN_OBJECT_LENGTH && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            return unescape(raw.substring(1, raw.length() - 1));
        }
        return raw;
    }

    private static String unescape(final String text) {
        final StringBuilder out = new StringBuilder(text.length());
        int pos = 0;
        while (pos < text.length()) {
            final char current = text.charAt(pos);
            if (current == '\\' && pos + 1 < text.length()) {
                pos += appendEscape(text, pos, out);
            } else {
                out.append(current);
                pos++;
            }
        }
        return out.toString();
    }

    /**
     * Interpreta um escape iniciado em {@code pos} e o anexa a {@code out},
     * devolvendo quantos caracteres foram consumidos.
     *
     * <p>Escape Unicode ({@code \\uXXXX}) com dígitos hexadecimais
     * malformados — possível em payload JWT de origem externa — não
     * propaga {@link NumberFormatException} (CodeQL
     * {@code java/uncaught-number-format-exception}, issue #1407): degrada
     * para o tratamento leniente de escape simples, preservando o texto
     * bruto, como já ocorre com escapes desconhecidos.</p>
     */
    private static int appendEscape(final String text, final int pos, final StringBuilder out) {
        final char next = text.charAt(pos + 1);
        if (next == 'u' && pos + UNICODE_ESCAPE_TOTAL < text.length()) {
            final String hex = text.substring(pos + UNICODE_HEX_START_OFFSET, pos + UNICODE_HEX_END_OFFSET);
            try {
                out.append((char) Integer.parseInt(hex, HEX_RADIX));
                return UNICODE_ESCAPE_CONSUMED;
            } catch (NumberFormatException ignored) {
                return appendSimpleEscape(next, out);
            }
        }
        return appendSimpleEscape(next, out);
    }

    private static int appendSimpleEscape(final char escaped, final StringBuilder out) {
        out.append(decodeSimpleEscape(escaped));
        return SIMPLE_ESCAPE_CONSUMED;
    }

    private static char decodeSimpleEscape(final char escaped) {
        return switch (escaped) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case 'b' -> '\b';
            case 'f' -> '\f';
            default -> escaped;
        };
    }
}
