package com.example.commerce.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommerceStateMachineTest {
    private final CommerceStateMachine machine = new CommerceStateMachine();

    @Test void requiresCartForCheckout() {
        var empty = new CommerceStateMachine.State(CartStatus.EMPTY, CheckoutStatus.NONE);
        assertThrows(IllegalStateException.class, () -> machine.requireCartForCheckout(empty));
    }

    @Test void successfulCartMutationInvalidatesCheckout() {
        var existing = new CommerceStateMachine.State(CartStatus.ACTIVE, CheckoutStatus.AWAITING_BUYER);
        assertEquals(new CommerceStateMachine.State(CartStatus.ACTIVE, CheckoutStatus.NONE),
            machine.cartChanged(existing, true));
    }

    @Test void removesLastItem() {
        var existing = new CommerceStateMachine.State(CartStatus.ACTIVE, CheckoutStatus.NONE);
        assertEquals(CartStatus.EMPTY, machine.cartChanged(existing, false).cart());
    }

    @Test void checkoutMustBeCreatedAfterRemoteSuccess() {
        var existing = new CommerceStateMachine.State(CartStatus.ACTIVE, CheckoutStatus.NONE);
        assertEquals(CheckoutStatus.AWAITING_BUYER, machine.checkoutCreated(existing).checkout());
        assertThrows(IllegalStateException.class, () -> machine.checkoutCreated(
            new CommerceStateMachine.State(CartStatus.ACTIVE, CheckoutStatus.AWAITING_BUYER)));
    }

    @Test void cannotCompletePaymentWithoutProviderConfirmation() {
        var existing = new CommerceStateMachine.State(CartStatus.ACTIVE, CheckoutStatus.AWAITING_BUYER);
        assertThrows(IllegalStateException.class, () -> machine.checkoutCompleted(existing, false));
    }
}
