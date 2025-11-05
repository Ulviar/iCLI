package com.github.ulviar.icli.fixture;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON parser supporting objects with nested objects, strings, numbers, and booleans.
 */
final class MiniJson {
    private final String text;
    private int index;

    private MiniJson(String text) {
        this.text = text;
    }

    static Map<String, Object> parseObject(String text) {
        MiniJson parser = new MiniJson(text.trim());
        parser.skipWhitespace();
        Map<String, Object> obj = parser.readObject();
        parser.skipWhitespace();
        if (!parser.isEnd()) {
            throw new IllegalArgumentException("Unexpected trailing content in JSON: " + text);
        }
        return obj;
    }

    private Map<String, Object> readObject() {
        if (peek() != '{') {
            throw new IllegalArgumentException("Expected '{'");
        }
        index++;
        skipWhitespace();
        Map<String, Object> map = new LinkedHashMap<>();
        if (peek() == '}') {
            index++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = readValue();
            map.put(key, value);
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                index++;
                continue;
            }
            if (c == '}') {
                index++;
                break;
            }
            throw new IllegalArgumentException("Expected ',' or '}' in object");
        }
        return map;
    }

    private Object readValue() {
        char c = peek();
        if (c == '"') {
            return readString();
        }
        if (c == '{') {
            return readObject();
        }
        if (c == 't' && text.startsWith("true", index)) {
            index += 4;
            return Boolean.TRUE;
        }
        if (c == 'f' && text.startsWith("false", index)) {
            index += 5;
            return Boolean.FALSE;
        }
        if ((c == '-') || Character.isDigit(c)) {
            return readNumber();
        }
        if (c == 'n' && text.startsWith("null", index)) {
            index += 4;
            return null;
        }
        throw new IllegalArgumentException("Unsupported JSON token at position " + index);
    }

    private Number readNumber() {
        int start = index;
        if (peek() == '-') {
            index++;
        }
        while (!isEnd() && Character.isDigit(peek())) {
            index++;
        }
        if (!isEnd() && peek() == '.') {
            index++;
            while (!isEnd() && Character.isDigit(peek())) {
                index++;
            }
            return Double.parseDouble(text.substring(start, index));
        }
        return Long.parseLong(text.substring(start, index));
    }

    private String readString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (true) {
            if (isEnd()) {
                throw new IllegalArgumentException("Unterminated string literal");
            }
            char c = text.charAt(index++);
            if (c == '\\') {
                if (isEnd()) {
                    throw new IllegalArgumentException("Invalid escape at end of input");
                }
                char esc = text.charAt(index++);
                switch (esc) {
                    case '"', '\\', '/' -> builder.append(esc);
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length()) {
                            throw new IllegalArgumentException("Invalid unicode escape");
                        }
                        String hex = text.substring(index, index + 4);
                        builder.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Unsupported escape \\" + esc + "\"");
                }
            } else if (c == '"') {
                break;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private void expect(char expected) {
        if (peek() != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' at position " + index);
        }
        index++;
    }

    private char peek() {
        if (isEnd()) {
            return '\0';
        }
        return text.charAt(index);
    }

    private void skipWhitespace() {
        while (!isEnd() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
    }

    private boolean isEnd() {
        return index >= text.length();
    }
}
