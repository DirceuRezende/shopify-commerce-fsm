package com.example.commerce.graph;

import com.example.commerce.domain.CommerceStateMachine;
import com.example.commerce.mcp.InvocationLog;
import com.example.commerce.mcp.ScopedTools;
import com.example.commerce.mcp.UcpPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

/** Only create_checkout is exposed. complete_checkout/payment is NEVER exposed. */
@Component
public class CheckoutNode {
    private final ChatClient chat;
    private final ScopedTools tools;
    private final ObjectMapper mapper;
    private final CommerceStateMachine fsm;
    public CheckoutNode(ChatModel model, ScopedTools tools, ObjectMapper mapper, CommerceStateMachine fsm) {
        this.chat = ChatClient.create(model); this.tools = tools; this.mapper = mapper; this.fsm = fsm;
    }
    public Map<String, Object> execute(CommerceState state) {
        try {
            fsm.requireCartForCheckout(state.businessState());
            var log = new InvocationLog();
            chat.prompt()
                .system("""
                    Você prepara uma sessão de checkout na Shopify, sem completar nem cobrar.
                    Use obrigatoriamente create_checkout com checkout.cart_id do contexto.
                    Se existir cart_id, não adicione novas line_items. Nunca invente ID.
                    """)
                .user("Criar checkout para o cartId confirmado: " + state.cartId())
                .tools(tools.forNode(Set.of("create_checkout"), state, log))
                .call().content();
            var called = log.last("create_checkout");
            if (called == null) throw new IllegalStateException("create_checkout não foi executada");
            var body = UcpPayloads.core(UcpPayloads.decode(mapper, called.rawResult()));
            String url = body.path("continue_url").asText("");
            if (url.isBlank()) throw new IllegalStateException("Checkout não tem continue_url confirmado");
            var next = fsm.checkoutCreated(state.businessState());
            return Map.of("checkoutStatus", next.checkout().name(),
                "checkoutId", UcpPayloads.id(body), "checkoutUrl", url,
                "reply", "Checkout criado. Revise e conclua sua compra na loja: " + url);
        } catch (Exception e) {
            return Map.of("error", "CHECKOUT_FAILED", "reply", "Checkout não criado: " + e.getMessage());
        }
    }
}
