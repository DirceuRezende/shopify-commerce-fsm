package com.example.commerce.domain;

import java.util.List;
import java.util.Map;

/** Resolve only a known variant (or a displayed result number) from saved search results. */
public final class ProductChoice {
    private ProductChoice() {}

    public static String select(String reference, List<Map<String, String>> results) {
        if (reference == null || reference.isBlank())
            throw new IllegalArgumentException("Informe a variante ou o número de um resultado.");
        var trimmed = reference.trim();
        if (trimmed.startsWith("gid://shopify/ProductVariant/")) return trimmed;
        try {
            int index = Integer.parseInt(trimmed) - 1;
            if (index < 0 || index >= results.size()) throw new NumberFormatException();
            String variantId = results.get(index).get("variantId");
            if (variantId == null || variantId.isBlank())
                throw new IllegalArgumentException("O produto selecionado não tem variante identificada. Consulte os detalhes antes de adicionar.");
            return variantId;
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("Escolha um número válido da última busca ou um ProductVariant GID.");
        }
    }
}
