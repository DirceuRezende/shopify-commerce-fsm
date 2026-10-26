package com.example.commerce.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProductChoiceTest {
    @Test void selectsOnlyRealVariantFromDisplayedPosition() {
        var results = List.of(
            Map.of("variantId", "gid://shopify/ProductVariant/101"),
            Map.of("variantId", "gid://shopify/ProductVariant/102"));
        assertEquals("gid://shopify/ProductVariant/102", ProductChoice.select("2", results));
        assertThrows(IllegalArgumentException.class, () -> ProductChoice.select("3", results));
    }

    @Test void refusesProductWithoutVariant() {
        assertThrows(IllegalArgumentException.class, () ->
            ProductChoice.select("1", List.of(Map.of("variantId", ""))));
    }
}
