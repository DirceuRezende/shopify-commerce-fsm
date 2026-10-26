package com.example.commerce.ucp;

import java.util.List;

/** Typed port. Only implementations decide how to call remote Shopify tools. */
public interface UcpGateway {
    record Discovery(String endpoint) {}
    record Product(String title, String variantId, String price) {}
    record LineItem(String variantId, int quantity) {}
    record Cart(String id, List<LineItem> items, String continueUrl) {}
    record Checkout(String id, String continueUrl, String providerStatus) {}

    Discovery discover(String merchantOrigin);
    List<String> listTools(String endpoint);
    List<Product> search(String endpoint, String query);
    Cart createCart(String endpoint, List<LineItem> items);
    Cart getCart(String endpoint, String cartId);
    Cart replaceCart(String endpoint, String cartId, List<LineItem> fullReplacement);
    Checkout createCheckout(String endpoint, String cartId);
}
