package dev.felnull.imp.client.bilibili;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cookie header for the Bilibili web APIs out of whatever was pasted: a JSON object (string members of the root or any
 * nested object) or a {@code ;}/newline-separated {@code name=value} list; {@code Path}/{@code Expires} are dropped and
 * the values are credentials, never logged.
 */
public final class BiliBiliCookie {
    private BiliBiliCookie() {
    }

    /** Cookie attributes that are never sent back to the API. */
    private static final java.util.Set<String> ATTRIBUTES = java.util.Set.of(
            "path", "expires", "max-age", "domain", "samesite", "secure", "httponly", "version", "comment");

    /**
     * @param text a Piliplus/JSON export, or a plain {@code name=value; …} string; {@code null}/blank ⇒ {@code ""}
     * @return the assembled {@code name=value; name=value} header (empty when nothing usable was found)
     */
    public static String parse(String text) {
        if (text == null || text.isBlank())
            return "";
        var trimmed = text.strip();

        if (trimmed.startsWith("{")) {
            var fromJson = fromJson(trimmed);
            if (!fromJson.isEmpty())
                return join(fromJson);
            // A JSON blob without a usable `cookies` object — or one the config field truncated on paste —
            // still gets scanned for known cookie pairs below.
            var scanned = scanKnownPairs(trimmed);
            if (!scanned.isEmpty())
                return join(scanned);
        }
        return join(fromPairs(trimmed));
    }

    /**
     * Names the Bilibili web APIs need (plus the fingerprint trio we fetch ourselves); only the last-resort scan uses
     * it, so an export's `"accessKey"` / `"refresh"` field can never end up in the header.
     */
    private static final java.util.Set<String> KNOWN_NAMES = java.util.Set.of(
            "sessdata", "bili_jct", "dedeuserid", "dedeuserid__ckmd5", "sid",
            "buvid3", "buvid4", "b_nut", "buvid_fp", "_uuid", "bili_ticket",
            "current_fnval", "current_quality", "rpd", "bsource", "fingerprint", "fingerprint2");

    /**
     * Last resort for a JSON-ish text that did not parse (truncated pastes included): regex out {@code "name": "value"}
     * pairs, keeping only {@link #KNOWN_NAMES}.
     */
    private static Map<String, String> scanKnownPairs(String text) {
        var out = new LinkedHashMap<String, String>();
        var matcher = java.util.regex.Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
        while (matcher.find()) {
            var name = matcher.group(1);
            var value = matcher.group(2);
            if (!KNOWN_NAMES.contains(name.toLowerCase(java.util.Locale.ROOT)) || value.isBlank())
                continue;
            out.putIfAbsent(name, value.replace("\r", "").replace("\n", "").strip());
        }
        return out;
    }

    /** The names found in a parse result — safe to log (the values are not). */
    public static String namesOf(String cookie) {
        if (cookie == null || cookie.isBlank())
            return "[]";
        var names = new java.util.ArrayList<String>();
        for (var part : cookie.split(";")) {
            var kv = part.strip();
            int eq = kv.indexOf('=');
            if (eq > 0)
                names.add(kv.substring(0, eq));
        }
        return names.toString();
    }

    // ------------------------------------------------------------------ JSON

    private static Map<String, String> fromJson(String json) {
        var out = new LinkedHashMap<String, String>();
        try {
            var root = JsonParser.parseString(json);
            if (!root.isJsonObject())
                return out;
            var rootObject = root.getAsJsonObject();

            // (a) the blob *is* the cookies object
            if (rootObject.has("cookies") && rootObject.get("cookies").isJsonObject()) {
                collect(rootObject.getAsJsonObject("cookies"), out);
            }
            // (b) a wrapper keyed by account id (Piliplus): take every `cookies` object found one level down
            for (var entry : rootObject.entrySet()) {
                var value = entry.getValue();
                if (value.isJsonObject() && value.getAsJsonObject().has("cookies")
                        && value.getAsJsonObject().get("cookies").isJsonObject()) {
                    collect(value.getAsJsonObject().getAsJsonObject("cookies"), out);
                }
            }
            // (c) last resort: the root looks like a plain name→value map of strings
            if (out.isEmpty())
                collect(rootObject, out);
        } catch (Exception ignored) {
            // Not JSON after all: the caller falls back to the plain-text path
        }
        return out;
    }

    private static void collect(JsonObject object, Map<String, String> out) {
        for (var entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                continue;
            var name = entry.getKey() == null ? "" : entry.getKey().strip();
            var text = value.getAsString();
            if (name.isEmpty() || text == null || text.isBlank() || ATTRIBUTES.contains(name.toLowerCase(java.util.Locale.ROOT)))
                continue;
            // Newlines inside a value would break the header
            out.put(name, text.replace("\r", "").replace("\n", "").strip());
        }
    }

    // ------------------------------------------------------------------ plain text

    private static Map<String, String> fromPairs(String text) {
        var out = new LinkedHashMap<String, String>();
        for (var chunk : text.split("[;\\r\\n]+")) {
            var part = chunk.strip();
            int eq = part.indexOf('=');
            if (eq <= 0)
                continue;
            var name = part.substring(0, eq).strip();
            var value = part.substring(eq + 1).strip();
            if (name.isEmpty() || value.isEmpty() || ATTRIBUTES.contains(name.toLowerCase(java.util.Locale.ROOT)))
                continue;
            // Quotes are common in hand-copied values ("…" from a dev-tools view)
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
                value = value.substring(1, value.length() - 1);
            out.put(name, value);
        }
        return out;
    }

    private static String join(Map<String, String> pairs) {
        var sb = new StringBuilder();
        for (var e : pairs.entrySet()) {
            if (sb.length() > 0)
                sb.append("; ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }
}
