package com.example.commerce.graph;

import com.example.commerce.domain.CommerceStateMachine;
import com.example.commerce.ucp.UcpGateway;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class CheckoutNode {
    private final CommerceStateMachine machine;
    private final UcpGateway gateway;
    public CheckoutNode(CommerceStateMachine machine, UcpGateway gateway) {
        this.machine = machine;
        this.gateway = gateway;
    }

    public Map<String, Object> execute(CommerceState state) {
        try {
            if (!state.supports("create_checkout")) throw new IllegalStateException("Tool create_checkout não anunciada no MCP");
            machine.requireCartForCheckout(state.businessState());
            // We NEVER invoke complete_checkout or handle payment credentials in this starter.
            var checkout = gateway.createCheckout(state.endpoint(), state.cartId());
            var next = machine.checkoutCreated(state.businessState());
            return Map.of(
                "checkoutId", checkout.id(),
                "checkoutUrl", checkout.continueUrl(),
                "checkoutStatus", next.checkout().name(),
                "reply", "Checkout criado. Revise e conclua sua compra com a loja: " + checkout.continueUrl()
            );
        } catch (RuntimeException ex) {
            return Map.of("error", "CHECKOUT_FAILED", "reply", "Checkout não criado: " + ex.getMessage());
        }
    }
}
