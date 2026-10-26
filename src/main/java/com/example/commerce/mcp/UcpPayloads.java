package com.example.commerce.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Parse actual callback responses (possibly structuredContent or content/text). */
public final class UcpPayloads {
    private UcpPayloads() {}
    public static JsonNode decode(ObjectMapper mapper, String raw) {
        try {
            JsonNode parsed = mapper.readTree(raw);
            if (parsed == null) throw new IllegalStateException("Resposta MCP vazia");
            if (parsed.path("isError").asBoolean(false) || parsed.hasNonNull("error"))
                throw new IllegalStateException("MCP retornou erro: " + parsed.path("error").asText("isError=true"));
            // Spring AI 2.0.1 SyncMcpToolCallback serializes the MCP content list,
            // not the full CallToolResult. Content text normally contains JSON.
            if (parsed.isArray() && !parsed.isEmpty() && parsed.get(0).path("text").isTextual())
                return decode(mapper, parsed.get(0).path("text").asText());
            JsonNode node = parsed.has("structuredContent") ? parsed.path("structuredContent") : parsed;
            if (node.has("result") && node.path("result").has("structuredContent"))
                node = node.path("result").path("structuredContent");
            if (node.has("content") && node.path("content").isArray() && !node.path("content").isEmpty()) {
                JsonNode text = node.path("content").get(0).path("text");
                if (text.isTextual()) return decode(mapper, text.asText());
            }
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("A tool não retornou JSON estruturado para atualização da FSM", e);
        }
    }
    public static String id(JsonNode node) {
        String result = node.path("id").asText("");
        if (result.isBlank() && node.has("cart")) result = node.path("cart").path("id").asText("");
        if (result.isBlank() && node.has("checkout")) result = node.path("checkout").path("id").asText("");
        if (result.isBlank()) throw new IllegalStateException("Resposta sem identificador confirmado");
        return result;
    }
    public static JsonNode core(JsonNode node) {
        if (node.has("cart")) return node.path("cart");
        if (node.has("checkout")) return node.path("checkout");
        return node;
    }
    public static String string(JsonNode node, String name) { return core(node).path(name).asText(""); }
    public static boolean hasCartItems(JsonNode node) { return core(node).path("line_items").isArray() && !core(node).path("line_items").isEmpty(); }

    public static List<Map<String, String>> products(JsonNode root) {
        List<Map<String, String>> out = new ArrayList<>();
        collect(root, out, 0);
        return List.copyOf(out);
    }
    private static void collect(JsonNode node, List<Map<String, String>> out, int depth) {
        if (node == null || depth > 14 || out.size() >= 10) return;
        if (node.isObject()) {
            String title = node.path("title").asText(node.path("name").asText(""));
            String variantId = node.path("variantId").asText(node.path("variant_id").asText(""));
            if (variantId.isEmpty() && node.path("id").asText("").startsWith("gid://shopify/ProductVariant/"))
                variantId = node.path("id").asText();
            if (!title.isBlank() && !out.stream().anyMatch(m -> m.get("title").equals(title))) {
                out.add(Map.of("title", title, "variantId", variantId,
                    "price", node.path("price").isValueNode() ? node.path("price").asText("") : ""));
            }
            node.elements().forEachRemaining(child -> collect(child, out, depth + 1));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collect(child, out, depth + 1));
        }
    }
}
