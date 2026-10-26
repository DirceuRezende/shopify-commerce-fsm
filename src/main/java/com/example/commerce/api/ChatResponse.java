package com.example.commerce.api;

import com.example.commerce.domain.CartStatus;
import com.example.commerce.domain.CheckoutStatus;
import com.example.commerce.domain.CommerceCommand;

import java.util.List;
import java.util.Map;

public record ChatResponse(
    String conversationId,
    CommerceCommand command,
    String reply,
    CartStatus cartStatus,
    CheckoutStatus checkoutStatus,
    String cartId,
    String checkoutId,
    String checkoutUrl,
    List<Map<String, String>> searchResults,
    String error
) {}
