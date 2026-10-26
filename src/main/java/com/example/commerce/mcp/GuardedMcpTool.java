package com.example.commerce.mcp;

import com.example.commerce.domain.CommerceCommand;
import com.example.commerce.domain.ProductChoice;
import com.example.commerce.graph.CommerceState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Set;

/** Wraps an EXISTING MCP ToolCallback; does not define/implement any UCP tool. */
public final class GuardedMcpTool implements ToolCallback {
    private final ToolCallback delegate;
    private final CommerceState state;
    private final InvocationLog log;
    private final ObjectMapper mapper;
    private final String agentProfile;
    private final Set<String> permitted;

    public GuardedMcpTool(ToolCallback delegate, CommerceState state, InvocationLog log,
                          ObjectMapper mapper, String agentProfile, Set<String> permitted) {
        this.delegate = delegate;
        this.state = state;
        this.log = log;
        this.mapper = mapper;
        this.agentProfile = agentProfile;
        this.permitted = permitted;
    }

    @Override public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
    @Override public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
    @Override public String call(String toolInput) { return call(toolInput, null); }

    @Override
    public String call(String toolInput, ToolContext context) {
        String name = getToolDefinition().name();
        if (permitted.stream().noneMatch(t -> McpToolCatalog.matches(name, t)))
            throw new SecurityException("Tool não permitida nesta etapa: " + name);
        if ((McpToolCatalog.matches(name, "create_cart") || McpToolCatalog.matches(name, "update_cart")
            || McpToolCatalog.matches(name, "create_checkout")) && log.last(name) != null)
            throw new SecurityException("Mutação repetida no mesmo turno: " + name);
        if (McpToolCatalog.matches(name, "create_checkout")) {
            if (state.command() != CommerceCommand.CREATE_CHECKOUT || state.cartId().isBlank()
                || state.cartStatus() != com.example.commerce.domain.CartStatus.ACTIVE
                || state.checkoutStatus() != com.example.commerce.domain.CheckoutStatus.NONE)
                throw new IllegalStateException("Checkout bloqueado pela FSM");
        }
        try {
            var root = mapper.readTree(toolInput);
            if (!(root instanceof ObjectNode args)) throw new IllegalArgumentException("Argumentos MCP devem ser um objeto JSON");
            ObjectNode meta = args.withObject("meta");
            meta.withObject("ucp-agent").put("profile", agentProfile);
            // The model never controls the ID of the checkout's cart.
            if (McpToolCatalog.matches(name, "create_checkout")) {
                ObjectNode checkout = args.withObject("checkout");
                checkout.put("cart_id", state.cartId());
                // Some server schemas accept line_items as empty when cart_id is present.
            }
            if (McpToolCatalog.matches(name, "get_cart") || McpToolCatalog.matches(name, "update_cart")) {
                if (state.cartId().isBlank()) throw new IllegalStateException("Carrinho não inicializado");
                args.put("id", state.cartId()); // Shopify Cart MCP takes id at top level.
            }
            if (McpToolCatalog.matches(name, "create_cart") || McpToolCatalog.matches(name, "update_cart")) {
                var op = state.command();
                if (op != CommerceCommand.ADD_TO_CART && op != CommerceCommand.REMOVE_FROM_CART)
                    throw new SecurityException("Nenhuma mutação solicitada pelo usuário");
                String variant = ProductChoice.select(state.string("productReference"), state.searchResults());
                JsonNode lines = args.path("cart").path("line_items");
                if (!lines.isArray()) throw new IllegalArgumentException("cart.line_items obrigatório");
                int quantityForVariant = 0;
                for (JsonNode line : lines) {
                    if (variant.equals(line.path("item").path("id").asText()))
                        quantityForVariant += line.path("quantity").asInt(0);
                }
                if (op == CommerceCommand.ADD_TO_CART && quantityForVariant < state.quantity())
                    throw new SecurityException("Variante/quantidade do MCP não corresponde ao pedido");
                if (op == CommerceCommand.REMOVE_FROM_CART && quantityForVariant != 0)
                    throw new SecurityException("A variante removida permanece no carrinho proposto");
                if (McpToolCatalog.matches(name, "update_cart")) {
                    var fetched = log.last("get_cart");
                    if (fetched == null) throw new SecurityException("Antes de update_cart é obrigatório get_cart");
                    JsonNode old = UcpPayloads.core(UcpPayloads.decode(mapper, fetched.rawResult()));
                    // Full-replacement semantics: preserve every other existing line item.
                    for (JsonNode prior : old.path("line_items")) {
                        String id = prior.path("item").path("id").asText();
                        if (id.equals(variant)) continue;
                        boolean kept = false;
                        for (JsonNode newer : lines)
                            if (id.equals(newer.path("item").path("id").asText())) kept = true;
                        if (!kept) throw new SecurityException("update_cart removeria outro item sem autorização");
                    }
                    for (String field : new String[]{"context", "buyer", "attribution", "signals"}) {
                        if (old.has(field) && !args.path("cart").has(field))
                            throw new SecurityException("update_cart removeria campo existente: " + field);
                    }
                }
            }
            String prepared = mapper.writeValueAsString(args);
            String output = context == null ? delegate.call(prepared) : delegate.call(prepared, context);
            // Reject MCP errors and don't record them as successful calls.
            UcpPayloads.decode(mapper, output);
            log.record(name, output);
            return output;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Argumentos JSON inválidos para a tool MCP", e);
        }
    }
}
