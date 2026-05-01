package io.sketch.mochaagents.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Safe JSON-compatible tree for Java ↔ Python remote kernels, aligned with smolagents {@code SafeSerializer}.
 *
 * <p>Uses {@code __type__} discriminators identical to Python for bytes, tuples, sets, timestamps, decimals, paths.
 * Pickle bridging is deliberately not implemented on the JVM emit path.</p>
 */
public final class SafeSerializer {

    public static final String SAFE_PREFIX = "safe:";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SafeSerializer() {}

    /** Convert a JVM value into a JSON tree object (Maps / Lists / wrappers). */
    @SuppressWarnings("unchecked")
    public static Object toJsonSafe(Object obj) throws SerializationError {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String || obj instanceof Boolean) {
            return obj;
        }
        if (obj instanceof Character ch) {
            return String.valueOf(ch);
        }
        if (obj instanceof Byte || obj instanceof Short || obj instanceof Integer
            || obj instanceof Long || obj instanceof Float || obj instanceof Double) {
            return obj;
        }
        if (obj instanceof BigDecimal bd) {
            return Map.of("__type__", "Decimal", "data", bd.toPlainString());
        }
        if (obj instanceof Enum<?> en) {
            return en.name();
        }
        if (obj instanceof byte[] b) {
            return Map.of("__type__", "bytes", "data", Base64.getEncoder().encodeToString(b));
        }
        if (obj instanceof Path p) {
            return Map.of("__type__", "Path", "data", p.toString());
        }
        if (obj instanceof Instant i) {
            return Map.of("__type__", "datetime", "data", i.toString());
        }
        if (obj instanceof LocalDateTime ldt) {
            return Map.of("__type__", "datetime", "data", ldt.toString());
        }
        if (obj instanceof LocalDate ld) {
            return Map.of("__type__", "date", "data", ld.toString());
        }
        if (obj instanceof LocalTime lt) {
            return Map.of("__type__", "time", "data", lt.toString());
        }
        if (obj instanceof Duration d) {
            return Map.of("__type__", "timedelta", "total_seconds", d.toSeconds());
        }
        if (obj.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(obj);
            List<Object> list = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                list.add(toJsonSafe(java.lang.reflect.Array.get(obj, i)));
            }
            return Map.of("__type__", "tuple", "data", list);
        }
        if (obj instanceof Collection<?> coll) {
            List<Object> list = new ArrayList<>(coll.size());
            for (Object item : coll) {
                list.add(toJsonSafe(item));
            }
            return list;
        }
        if (obj instanceof Map<?, ?> raw) {
            boolean allStrKeys =
                raw.keySet().stream().allMatch(k -> k instanceof String || k == null);
            if (allStrKeys) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : raw.entrySet()) {
                    Object k = e.getKey();
                    if (k == null) {
                        throw new SerializationError("Null map keys are not JSON-safe.");
                    }
                    out.put((String) k, toJsonSafe(e.getValue()));
                }
                return out;
            }
            List<List<Object>> pairs = new ArrayList<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                pairs.add(List.of(toJsonSafe(e.getKey()), toJsonSafe(e.getValue())));
            }
            return Map.of("__type__", "dict_with_complex_keys", "data", pairs);
        }
        throw new SerializationError("Cannot safely serialize Java type " + obj.getClass().getName());
    }

    public static String dumps(Object obj, boolean allowPickleIgnored) throws SerializationError {
        Object tree = toJsonSafe(obj);
        try {
            return SAFE_PREFIX + MAPPER.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            throw new SerializationError("JSON encode failed", e);
        }
    }

    public static JsonNode loadsJsonTree(String prefixedOrRaw) throws SerializationError {
        String body = prefixedOrRaw;
        if (body.startsWith(SAFE_PREFIX)) {
            body = body.substring(SAFE_PREFIX.length());
        }
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            throw new SerializationError("JSON parse failed", e);
        }
    }

    /** Python source defining {@code _mocha_from_js} used after {@code json.loads(...)}. */
    public static String embeddedPythonFromJsonSafeFunction() {
        return """
            import base64
            from datetime import date, datetime, time, timedelta
            from decimal import Decimal
            from pathlib import Path

            def _mocha_from_js(obj):
                if isinstance(obj, list):
                    return [_mocha_from_js(x) for x in obj]
                if isinstance(obj, dict):
                    if "__type__" in obj:
                        t = obj["__type__"]
                        if t == "bytes":
                            return base64.b64decode(obj["data"])
                        if t == "tuple":
                            return tuple(_mocha_from_js(x) for x in obj["data"])
                        if t == "set":
                            return set(_mocha_from_js(x) for x in obj["data"])
                        if t == "frozenset":
                            return frozenset(_mocha_from_js(x) for x in obj["data"])
                        if t == "dict_with_complex_keys":
                            return {_mocha_from_js(k): _mocha_from_js(v) for k, v in obj["data"]}
                        if t == "complex":
                            return complex(obj["real"], obj["imag"])
                        if t == "datetime":
                            return datetime.fromisoformat(obj["data"])
                        if t == "date":
                            return date.fromisoformat(obj["data"])
                        if t == "time":
                            return time.fromisoformat(obj["data"])
                        if t == "timedelta":
                            return timedelta(seconds=obj["total_seconds"])
                        if t == "Decimal":
                            return Decimal(obj["data"])
                        if t == "Path":
                            return Path(obj["data"])
                        if t == "PIL.Image":
                            try:
                                from io import BytesIO
                                import PIL.Image
                                return PIL.Image.open(BytesIO(base64.b64decode(obj["data"])))
                            except ImportError:
                                return obj
                        if t == "ndarray":
                            try:
                                import numpy as np
                                return np.array(obj["data"], dtype=obj["dtype"])
                            except ImportError:
                                return obj["data"]
                    return {k: _mocha_from_js(v) for k, v in obj.items()}
                return obj
            """.strip() + "\n";
    }

    /** Build Python code: decode base64 JSON and assign {@code vars_dict} via {@code _mocha_from_js}. */
    public static String pythonAssignVarsFromSafeJson(Map<String, Object> variables, String indent) throws SerializationError {
        if (variables == null || variables.isEmpty()) {
            return indent + "vars_dict = {}\n";
        }
        Object tree = toJsonSafe(variables);
        String json;
        try {
            json = MAPPER.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            throw new SerializationError("JSON encode failed", e);
        }
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        sb.append(embeddedPythonFromJsonSafeFunction()).append("\n");
        sb.append(indent).append("import json, base64\n");
        sb.append(indent).append("vars_dict = _mocha_from_js(json.loads(base64.b64decode(\"");
        sb.append(b64);
        sb.append("\")))\n");
        return sb.toString();
    }

    /** Best-effort convert safe JSON payload into JDK-friendly objects for final_answer decoding. */
    public static Object fromJsonSafe(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.doubleValue();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>(node.size());
            for (JsonNode n : node) {
                list.add(fromJsonSafe(n));
            }
            return list;
        }
        if (node.isObject()) {
            if (node.has("__type__")) {
                String t = node.path("__type__").asText("");
                return switch (t) {
                    case "bytes" -> Base64.getDecoder().decode(node.path("data").asText(""));
                    case "tuple" -> {
                        List<Object> list = new ArrayList<>();
                        for (JsonNode n : node.path("data")) {
                            list.add(fromJsonSafe(n));
                        }
                        yield list.toArray();
                    }
                    case "set", "frozenset" -> {
                        List<Object> elems = new ArrayList<>();
                        for (JsonNode n : node.path("data")) {
                            elems.add(fromJsonSafe(n));
                        }
                        yield Map.of("__type__", t, "data", elems);
                    }
                    case "Decimal" -> new BigDecimal(node.path("data").asText());
                    case "Path" -> Path.of(node.path("data").asText());
                    default -> nestedMapWithoutSpecial(node);
                };
            }
            return nestedMapWithoutSpecial(node);
        }
        return node.toString();
    }

    private static Map<String, Object> nestedMapWithoutSpecial(JsonNode node) {
        Map<String, Object> m = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> m.put(e.getKey(), fromJsonSafe(e.getValue())));
        return m;
    }

    /** Decode smolagents {@code safe:…} payloads into JDK objects where possible. */
    public static Object loads(String data) throws SerializationError {
        JsonNode tree = loadsJsonTree(data);
        return fromJsonSafe(tree);
    }
}
