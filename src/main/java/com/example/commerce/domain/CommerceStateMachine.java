package com.example.commerce.domain;

/** Pure Java rules: the LLM NEVER writes these statuses. */
public final class CommerceStateMachine {
    public record State(CartStatus cart, CheckoutStatus checkout) {}

    public void requireCartForCheckout(State current) {
        if (current.cart() != CartStatus.ACTIVE)
            throw new IllegalStateException("Não existe um carrinho ativo para criar o checkout.");
        if (current.checkout() != CheckoutStatus.NONE)
            throw new IllegalStateException("Já existe um checkout pendente para este carrinho.");
    }

    public State cartChanged(State current, boolean hasItems) {
        // A mutation invalidates any checkout previously prepared against the older cart.
        return new State(hasItems ? CartStatus.ACTIVE : CartStatus.EMPTY, CheckoutStatus.NONE);
    }

    public State checkoutCreated(State current) {
        requireCartForCheckout(current);
        return new State(current.cart(), CheckoutStatus.AWAITING_BUYER);
    }

    public State checkoutCompleted(State current, boolean providerConfirmed) {
        if (!providerConfirmed || current.checkout() != CheckoutStatus.AWAITING_BUYER)
            throw new IllegalStateException("Checkout não confirmado pelo provedor.");
        return new State(current.cart(), CheckoutStatus.COMPLETED);
    }
}
