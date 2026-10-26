package com.example.commerce.graph;

import com.example.commerce.domain.CommerceCommand;
import com.example.commerce.domain.CommerceStateMachine;
import com.example.commerce.domain.ProductChoice;
import com.example.commerce.ucp.UcpGateway;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CartNode {
    private final UcpGateway gateway;
    private final CommerceStateMachine machine;
    public CartNode(UcpGateway gateway, CommerceStateMachine machine) {
        this.gateway = gateway;
        this.machine = machine;
    }

    public Map<String, Object> execute(CommerceState state) {
        try {
            if (!state.supports("get_cart") || !state.supports("create_cart") || !state.supports("update_cart"))
                throw new IllegalStateException("Tools de carrinho não anunciadas no MCP");
            if (state.command() == CommerceCommand.VIEW_CART) {
                if (state.cartId().isBlank())
                    return Map.of("reply", "Seu carrinho está vazio.");
                var current = gateway.getCart(state.endpoint(), state.cartId());
                return Map.of("reply", "Carrinho com " + current.items().size() +
                    " tipo(s) de item(ns). Link: " + current.continueUrl());
            }

            String variant = ProductChoice.select(state.string("productReference"), state.searchResults());
            var lines = new ArrayList<UcpGateway.LineItem>();
            if (!state.cartId().isBlank()) lines.addAll(gateway.getCart(state.endpoint(), state.cartId()).items());
            int existing = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (variant.equals(lines.get(i).variantId())) { existing = i; break; }
            }

            if (state.command() == CommerceCommand.ADD_TO_CART) {
                int quantity = state.quantity();
                if (quantity < 1 || quantity > 99) throw new IllegalArgumentException("Quantidade deve estar entre 1 e 99.");
                if (existing >= 0) lines.set(existing, new UcpGateway.LineItem(variant, lines.get(existing).quantity() + quantity));
                else lines.add(new UcpGateway.LineItem(variant, quantity));
            } else if (state.command() == CommerceCommand.REMOVE_FROM_CART) {
                if (existing < 0) throw new IllegalArgumentException("Variante não existe no carrinho.");
                lines.remove(existing);
            } else throw new IllegalArgumentException("Comando não autorizado para CartNode");

            UcpGateway.Cart updated = state.cartId().isBlank()
                ? gateway.createCart(state.endpoint(), lines)
                : gateway.replaceCart(state.endpoint(), state.cartId(), lines);
            var next = machine.cartChanged(state.businessState(), !updated.items().isEmpty());
            return Map.of(
                "cartId", updated.id(),
                "cartStatus", next.cart().name(),
                "checkoutStatus", next.checkout().name(),
                "checkoutId", "", // Cart mutation invalidates pending checkout.
                "checkoutUrl", "",
                "reply", "Carrinho atualizado com sucesso. " + updated.items().size()
                    + " tipo(s) de item(ns). " + updated.continueUrl()
            );
        } catch (RuntimeException ex) {
            return Map.of("error", "CART_FAILED", "reply", "Não alterei o carrinho: " + ex.getMessage());
        }
    }
}
