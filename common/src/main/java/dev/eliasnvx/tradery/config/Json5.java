package dev.eliasnvx.tradery.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/**
 * A small JSON5 reader and writer on top of Gson's tree model.
 *
 * <p>Reads everything JSON5 allows: comments, unquoted keys, single-quoted strings, trailing commas, hex numbers,
 * leading/trailing decimal points, {@code +} signs, {@code Infinity} and {@code NaN}. Decimal numbers become
 * {@link BigDecimal}s so money values keep every digit.
 *
 * <p>Writes indented JSON5 with {@code //} comments taken from a map of dotted paths ({@code "ore.dailyCap"}).
 */
public final class Json5 {
    private final String text;
    private int pos;

    private Json5(String text) {
        this.text = text;
    }

    /** Thrown for malformed input; the message says where. */
    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    public static JsonElement parse(String text) throws ParseException {
        Json5 reader = new Json5(text.startsWith("﻿") ? text.substring(1) : text);
        reader.skipIgnored();
        JsonElement value = reader.readValue();
        reader.skipIgnored();
        if (reader.pos < reader.text.length()) {
            throw reader.error("Unexpected '" + reader.text.charAt(reader.pos) + "' after the end of the document");
        }
        return value;
    }

    // ---------------------------------------------------------------- reading

    private JsonElement readValue() throws ParseException {
        if (pos >= text.length()) {
            throw error("Unexpected end of input, expected a value");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"', '\'' -> new JsonPrimitive(readString());
            default -> {
                if (c == '-' || c == '+' || c == '.' || (c >= '0' && c <= '9')) {
                    yield readNumber();
                }
                String word = readIdentifier();
                yield switch (word) {
                    case "true" -> new JsonPrimitive(true);
                    case "false" -> new JsonPrimitive(false);
                    case "null" -> JsonNull.INSTANCE;
                    case "Infinity" -> new JsonPrimitive(Double.POSITIVE_INFINITY);
                    case "NaN" -> new JsonPrimitive(Double.NaN);
                    default -> throw error("Unexpected '" + (word.isEmpty() ? String.valueOf(c) : word) + "', expected a value");
                };
            }
        };
    }

    private JsonObject readObject() throws ParseException {
        JsonObject object = new JsonObject();
        pos++; // {
        skipIgnored();
        while (true) {
            if (peek() == '}') {
                pos++;
                return object;
            }
            String key = peek() == '"' || peek() == '\'' ? readString() : readIdentifier();
            if (key.isEmpty()) {
                throw error("Expected a key or '}'");
            }
            skipIgnored();
            expect(':');
            skipIgnored();
            if (object.has(key)) {
                throw error("Duplicate key '" + key + "'");
            }
            object.add(key, readValue());
            skipIgnored();
            if (peek() == ',') {
                pos++;
                skipIgnored();
            } else if (peek() != '}') {
                throw error("Expected ',' or '}'");
            }
        }
    }

    private JsonArray readArray() throws ParseException {
        JsonArray array = new JsonArray();
        pos++; // [
        skipIgnored();
        while (true) {
            if (peek() == ']') {
                pos++;
                return array;
            }
            array.add(readValue());
            skipIgnored();
            if (peek() == ',') {
                pos++;
                skipIgnored();
            } else if (peek() != ']') {
                throw error("Expected ',' or ']'");
            }
        }
    }

    private String readString() throws ParseException {
        char quote = text.charAt(pos++);
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("Unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == quote) {
                return out.toString();
            }
            if (c == '\n' || c == '\r') {
                throw error("Line break inside a string (escape it with '\\')");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("Unterminated escape");
            }
            char e = text.charAt(pos++);
            switch (e) {
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'v' -> out.append('\u000B');
                case '0' -> out.append('\0');
                case 'x' -> out.append((char) readHex(2));
                case 'u' -> out.append((char) readHex(4));
                case '\r' -> {
                    if (pos < text.length() && text.charAt(pos) == '\n') {
                        pos++; // line continuation over \r\n
                    }
                }
                case '\n', ' ', ' ' -> {
                    // line continuation: nothing
                }
                default -> out.append(e); // \' \" \\ \/ and any other char stand for themselves
            }
        }
    }

    private int readHex(int digits) throws ParseException {
        if (pos + digits > text.length()) {
            throw error("Unterminated escape");
        }
        try {
            int value = Integer.parseInt(text.substring(pos, pos + digits), 16);
            pos += digits;
            return value;
        } catch (NumberFormatException e) {
            throw error("Bad hex escape");
        }
    }

    private JsonPrimitive readNumber() throws ParseException {
        int start = pos;
        boolean negative = false;
        if (peek() == '+' || peek() == '-') {
            negative = text.charAt(pos) == '-';
            pos++;
        }
        if (text.startsWith("Infinity", pos)) {
            pos += "Infinity".length();
            return new JsonPrimitive(negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        }
        if (text.startsWith("NaN", pos)) {
            pos += "NaN".length();
            return new JsonPrimitive(Double.NaN);
        }
        if (text.startsWith("0x", pos) || text.startsWith("0X", pos)) {
            pos += 2;
            int hexStart = pos;
            while (pos < text.length() && Character.digit(text.charAt(pos), 16) >= 0) {
                pos++;
            }
            if (pos == hexStart) {
                throw error("Expected hex digits");
            }
            BigInteger value = new BigInteger(text.substring(hexStart, pos), 16);
            return new JsonPrimitive(new BigDecimal(negative ? value.negate() : value));
        }
        int digitsStart = pos;
        while (pos < text.length() && isNumberChar(text.charAt(pos))) {
            pos++;
        }
        String number = text.substring(digitsStart, pos);
        if (number.startsWith(".")) {
            number = "0" + number;
        }
        if (number.endsWith(".")) {
            number = number + "0";
        }
        number = number.replace(".e", ".0e").replace(".E", ".0E");
        try {
            BigDecimal value = new BigDecimal(number);
            return new JsonPrimitive(negative ? value.negate() : value);
        } catch (NumberFormatException e) {
            pos = start;
            throw error("Bad number '" + text.substring(start, digitsStart) + number + "'");
        }
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
    }

    private String readIdentifier() {
        int start = pos;
        if (pos < text.length() && (Character.isJavaIdentifierStart(text.charAt(pos)))) {
            pos++;
            while (pos < text.length() && Character.isJavaIdentifierPart(text.charAt(pos)) && text.charAt(pos) != '\u0000') {
                pos++;
            }
        }
        return text.substring(start, pos);
    }

    private void skipIgnored() throws ParseException {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                while (pos < text.length() && text.charAt(pos) != '\n') {
                    pos++;
                }
            } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '*') {
                int end = text.indexOf("*/", pos + 2);
                if (end < 0) {
                    throw error("Unterminated comment");
                }
                pos = end + 2;
            } else if (Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '﻿') {
                pos++;
            } else {
                return;
            }
        }
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private void expect(char c) throws ParseException {
        if (peek() != c) {
            throw error("Expected '" + c + "'");
        }
        pos++;
    }

    private ParseException error(String message) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < Math.min(pos, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new ParseException(message + " at line " + line + ", column " + column);
    }

    // ---------------------------------------------------------------- writing

    /**
     * Writes a JSON5 document.
     *
     * @param root     the value
     * @param comments comments by dotted path ({@code ""} = above the root); multi-line comments allowed
     * @param header   lines written at the top as {@code //} comments, may be empty
     */
    public static String write(JsonElement root, Map<String, String> comments, String header) {
        StringBuilder out = new StringBuilder();
        if (!header.isEmpty()) {
            appendComment(out, header, "");
        }
        String rootComment = comments.get("");
        if (rootComment != null) {
            appendComment(out, rootComment, "");
        }
        writeValue(out, root, comments, "", "");
        return out.append('\n').toString();
    }

    private static void writeValue(StringBuilder out, JsonElement value, Map<String, String> comments, String path, String indent) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.size() == 0) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            String inner = indent + "  ";
            int i = 0;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                String comment = comments.get(childPath);
                if (comment != null) {
                    if (i > 0) {
                        out.append('\n');
                    }
                    appendComment(out, comment, inner);
                }
                out.append(inner).append(key(entry.getKey())).append(": ");
                writeValue(out, entry.getValue(), comments, childPath, inner);
                if (++i < object.size()) {
                    out.append(',');
                }
                out.append('\n');
            }
            out.append(indent).append('}');
        } else if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (array.isEmpty()) {
                out.append("[]");
                return;
            }
            boolean inline = true;
            int length = 0;
            for (JsonElement element : array) {
                inline &= element.isJsonPrimitive() || element.isJsonNull();
                length += element.toString().length() + 2;
            }
            if (inline && length <= 80) {
                out.append('[');
                for (int i = 0; i < array.size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    writeValue(out, array.get(i), comments, path, indent);
                }
                out.append(']');
                return;
            }
            out.append("[\n");
            String inner = indent + "  ";
            for (int i = 0; i < array.size(); i++) {
                out.append(inner);
                writeValue(out, array.get(i), comments, path, inner);
                if (i + 1 < array.size()) {
                    out.append(',');
                }
                out.append('\n');
            }
            out.append(indent).append(']');
        } else if (value.isJsonNull()) {
            out.append("null");
        } else {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isString()) {
                out.append(quote(primitive.getAsString()));
            } else if (primitive.isNumber()) {
                out.append(number(primitive.getAsNumber()));
            } else {
                out.append(primitive.getAsBoolean());
            }
        }
    }

    private static String number(Number number) {
        if (number instanceof Double d) {
            if (d.isNaN()) {
                return "NaN";
            }
            if (d.isInfinite()) {
                return d > 0 ? "Infinity" : "-Infinity";
            }
            return new BigDecimal(d.toString()).stripTrailingZeros().toPlainString();
        }
        if (number instanceof Float f) {
            return number(f.doubleValue());
        }
        if (number instanceof BigDecimal big) {
            BigDecimal stripped = big.stripTrailingZeros();
            return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
        }
        return number.toString();
    }

    private static void appendComment(StringBuilder out, String comment, String indent) {
        for (String line : comment.split("\n", -1)) {
            out.append(indent).append("//");
            if (!line.isEmpty()) {
                out.append(' ').append(line);
            }
            out.append('\n');
        }
    }

    private static String key(String key) {
        if (key.isEmpty() || !Character.isJavaIdentifierStart(key.charAt(0)) || key.charAt(0) == '$') {
            return quote(key);
        }
        for (int i = 1; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!Character.isJavaIdentifierPart(c) || c == '$' || Character.isIdentifierIgnorable(c)) {
                return quote(key);
            }
        }
        return switch (key) {
            case "true", "false", "null", "Infinity", "NaN" -> quote(key);
            default -> key;
        };
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
