package com.nanolaba.logging.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal strict JSON parser for tests: objects become {@link LinkedHashMap}s (key order kept), arrays
 * {@link ArrayList}s, numbers {@link BigDecimal}s. Anything that is not valid JSON, including raw control characters
 * inside strings and trailing content, throws {@link IllegalArgumentException}.
 */
public final class StrictJson {

    private final String text;
    private int pos;

    private StrictJson(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        StrictJson parser = new StrictJson(text);
        parser.skipWhitespace();
        Object value = parser.value();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("trailing content");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("not a JSON object: " + text);
        }
        return (Map<String, Object>) value;
    }

    private Object value() {
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> result = new LinkedHashMap<>();
        pos++;
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("expected a key");
            }
            String key = string();
            if (result.containsKey(key)) {
                throw error("duplicate key " + key);
            }
            skipWhitespace();
            expect(':');
            skipWhitespace();
            result.put(key, value());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return result;
            }
            if (c != ',') {
                throw error("expected , or }");
            }
        }
    }

    private List<Object> array() {
        List<Object> result = new ArrayList<>();
        pos++;
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            result.add(value());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return result;
            }
            if (c != ',') {
                throw error("expected , or ]");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return result.toString();
            } else if (c < 0x20) {
                throw error("raw control character in a string");
            } else if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"':
                    case '\\':
                    case '/':
                        result.append(e);
                        break;
                    case 'b':
                        result.append('\b');
                        break;
                    case 'f':
                        result.append('\f');
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw error("short unicode escape");
                        }
                        result.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw error("invalid escape \\" + e);
                }
            } else {
                result.append(c);
            }
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, pos)) {
            throw error("expected " + word);
        }
        pos += word.length();
        return value;
    }

    private BigDecimal number() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw error("unexpected character");
        }
        try {
            return new BigDecimal(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("invalid number");
        }
    }

    private void skipWhitespace() {
        while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        return text.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char expected) {
        if (next() != expected) {
            throw error("expected " + expected);
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at " + pos + " in: " + text);
    }
}
