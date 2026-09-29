package dev.eliasnvx.tradery.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds what a lenient decode silently dropped: compares the file as written with the decoded config encoded
 * back. Every value that didn't survive was invalid (and replaced by its default) or is an unknown key.
 */
public final class ConfigDiff {
    private ConfigDiff() {
    }

    public static List<String> problems(JsonElement written, JsonElement decoded) {
        List<String> problems = new ArrayList<>();
        compare("", written, decoded, problems);
        return problems;
    }

    private static void compare(String path, JsonElement written, JsonElement decoded, List<String> problems) {
        if (written.isJsonObject()) {
            JsonObject object = written.getAsJsonObject();
            JsonObject target = decoded != null && decoded.isJsonObject() ? decoded.getAsJsonObject() : null;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                JsonElement counterpart = target == null ? null : target.get(entry.getKey());
                if (counterpart == null) {
                    problems.add(target != null && !entry.getValue().isJsonNull()
                        ? "'" + childPath + "' is unknown or invalid and was ignored"
                        : "'" + childPath + "' is invalid, the default is used");
                } else {
                    compare(childPath, entry.getValue(), counterpart, problems);
                }
            }
            return;
        }
        if (written.isJsonArray()) {
            JsonArray array = written.getAsJsonArray();
            if (decoded == null || !decoded.isJsonArray() || decoded.getAsJsonArray().size() != array.size()) {
                problems.add("'" + path + "' is invalid, the default is used");
                return;
            }
            for (int i = 0; i < array.size(); i++) {
                compare(path + "[" + i + "]", array.get(i), decoded.getAsJsonArray().get(i), problems);
            }
            return;
        }
        if (written.isJsonNull()) {
            return; // null means "use the default"
        }
        if (decoded == null || !sameValue(written.getAsJsonPrimitive(), decoded)) {
            problems.add("'" + path + "' = " + written + " is invalid, the default is used");
        }
    }

    private static boolean sameValue(JsonPrimitive written, JsonElement decoded) {
        if (!decoded.isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive other = decoded.getAsJsonPrimitive();
        if (written.isNumber() && other.isNumber()) {
            try {
                return new BigDecimal(written.getAsNumber().toString()).compareTo(new BigDecimal(other.getAsNumber().toString())) == 0;
            } catch (NumberFormatException e) {
                return written.getAsDouble() == other.getAsDouble();
            }
        }
        if (written.isString() && other.isString()) {
            return written.getAsString().equalsIgnoreCase(other.getAsString()); // enums are case-insensitive
        }
        if (written.isNumber() && other.isString() || written.isString() && other.isNumber()) {
            try {
                return new BigDecimal(written.getAsString().trim()).compareTo(new BigDecimal(other.getAsString().trim())) == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return written.equals(other);
    }
}
