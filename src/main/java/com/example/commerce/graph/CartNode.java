package com.example.commerce.graph;

import com.example.commerce.domain.CommerceCommand;
import com.example.commerce.domain.CommerceStateMachine;
import com.example.commerce.domain.ProductChoice;
import com.example.commerce.mcp.InvocationLog;
import com.example.commerce.mcp.ScopedTools;
import com.example.commerce.mcp.UcpPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

/** LLM chooses remote Cart MCP tools, never changes cartStatus by itself. */
@Component
public class CartNode {
    private final ChatClient chat;
    private final ScopedTools tools;
    private final ObjectMapper mapper;
    private final CommerceStateMachine fsm;
    public CartNode(ChatModel model, ScopedTools tools, ObjectMapper mapper, CommerceStateMachine fsm) {
        this.chat = ChatClient.create(model); this.tools = tools; this.mapper = mapper; this.fsm = fsm;
    }

    public Map<String, Object> execute(CommerceState state) {
        try {
            var command = state.command();
            var allowed = switch (command) {
                case ADD_TO_CART -> state.cartId().isBlank() ? Set.of("create_cart")
                    : Set.of("get_cart", "update_cart");
                case REMOVE_FROM_CART -> Set.of("get_cart", "update_cart");
                case VIEW_CART -> Set.of("get_cart");
                default -> throw new IllegalArgumentException("Evento não é de carrinho");
            };
            String variant = "";
            if (command == CommerceCommand.ADD_TO_CART || command == CommerceCommand.REMOVE_FROM_CART)
                variant = ProductChoice.select(state.string("productReference"), state.searchResults());
            if (command != CommerceCommand.ADD_TO_CART && state.cartId().isBlank())
                throw new IllegalStateException("Não existe carrinho ativo");
            var log = new InvocationLog();
            var reply = chat.prompt()
                .system("""
                    Você é o agente de carrinho de uma loja Shopify.
                    As ferramentas são o Cart MCP REAL, com seus schemas oficiais.
                    Utilize uma ferramenta apropriada; não invente IDs. Para update_cart,
                    obtenha o carrinho e mantenha todos os line_items/context/buyer necessários:
                    update_cart substitui o documento do carrinho, não é um PATCH parcial.
                    Não simule sucesso e não invoque checkout.
                    """)
                .user("Evento=" + command + "; variante autorizada=" + variant +
                    "; quantidade=" + state.quantity() + "; cartId=" + state.cartId() +
                    "; instrução original=" + state.string("userMessage"))
                .tools(tools.forNode(allowed, state, log))
                .call().content();
            var mutation = log.last(state.cartId().isBlank() ? "create_cart" : "update_cart");
            if (command == CommerceCommand.VIEW_CART) {
                var read = log.last("get_cart");
                if (read == null) throw new IllegalStateException("get_cart não foi chamada");
                var payload = UcpPayloads.decode(mapper, read.rawResult());
                return Map.of("reply", reply == null ? "Carrinho consultado." : reply,
                    "cartId", UcpPayloads.id(UcpPayloads.core(payload)));
            }
            if (mutation == null) throw new IllegalStateException("A LLM não executou a mutação solicitada");
            var payload = UcpPayloads.core(UcpPayloads.decode(mapper, mutation.rawResult()));
            var id = UcpPayloads.id(payload);
            if (!UcpPayloads.hasCartItems(payload) && !payload.path("line_items").isArray())
                throw new IllegalStateException("MCP não forneceu line_items confirmados");
            var next = fsm.cartChanged(state.businessState(), UcpPayloads.hasCartItems(payload));
            return Map.of(
                "cartId", id,
                "cartStatus", next.cart().name(),
                "checkoutStatus", next.checkout().name(),
                "checkoutId", "",
                "checkoutUrl", "",
                "reply", reply == null ? "Carrinho atualizado." : reply);
        } catch (Exception e) {
            return Map.of("error", "CART_FAILED", "reply", "Não alterei o carrinho: " + e.getMessage());
        }
    }
}
