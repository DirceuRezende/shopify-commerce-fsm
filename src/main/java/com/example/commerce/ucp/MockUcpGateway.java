package com.example.commerce.ucp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "commerce.mode", havingValue = "mock", matchIfMissing = true)
public class MockUcpGateway implements UcpGateway {
    private final Map<String, Cart> carts = new ConcurrentHashMap<>();
    private static final List<Product> CATALOG = List.of(
        new Product("Tênis Running Azul", "gid://shopify/ProductVariant/101", "R$ 299,90"),
        new Product("Tênis Running Preto", "gid://shopify/ProductVariant/102", "R$ 349,90"),
        new Product("Camiseta Dry Fit", "gid://shopify/ProductVariant/103", "R$ 79,90")
    );

    @Override public Discovery discover(String merchantOrigin) {
        return new Discovery(merchantOrigin + "/api/ucp/mcp");
    }

    @Override public List<String> listTools(String endpoint) {
        return List.of("search_catalog", "lookup_catalog", "get_product", "create_cart", "get_cart",
            "update_cart", "cancel_cart", "create_checkout", "get_checkout", "update_checkout");
    }

    @Override public List<Product> search(String endpoint, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return CATALOG.stream().filter(p -> q.isBlank() || p.title().toLowerCase(Locale.ROOT).contains(q)
            || q.contains("tenis") && p.title().toLowerCase(Locale.ROOT).contains("tênis")
            || q.contains("tênis") && p.title().toLowerCase(Locale.ROOT).contains("tênis"))
            .toList();
    }

    @Override public Cart createCart(String endpoint, List<LineItem> items) {
        String id = "gid://shopify/Cart/demo_" + UUID.randomUUID();
        Cart cart = new Cart(id, List.copyOf(items), "https://demo.myshopify.com/cart");
        carts.put(id, cart);
        return cart;
    }

    @Override public Cart getCart(String endpoint, String cartId) {
        Cart found = carts.get(cartId);
        if (found == null) throw new IllegalStateException("Carrinho mock não encontrado (reiniciou a aplicação?).");
        return found;
    }

    @Override public Cart replaceCart(String endpoint, String cartId, List<LineItem> items) {
        Cart current = getCart(endpoint, cartId);
        Cart updated = new Cart(cartId, List.copyOf(items), current.continueUrl());
        carts.put(cartId, updated);
        return updated;
    }

    @Override public Checkout createCheckout(String endpoint, String cartId) {
        Cart cart = getCart(endpoint, cartId);
        if (cart.items().isEmpty()) throw new IllegalArgumentException("Carrinho vazio");
        String id = "gid://shopify/Checkout/demo_" + UUID.randomUUID();
        return new Checkout(id, "https://demo.myshopify.com/checkouts/demo", "requires_escalation");
    }
}
