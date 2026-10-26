package com.example.commerce.graph;

import com.example.commerce.domain.CartStatus;
import com.example.commerce.domain.CheckoutStatus;
import com.example.commerce.domain.CommerceCommand;
import com.example.commerce.domain.CommerceStateMachine;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.List;
import java.util.Map;

/** Keys are plain serializable JSON-like data; LangGraph4j checkpoints handle conversation persistence. */
public class CommerceState extends AgentState {
    public static final Map<String, Channel<?>> SCHEMA = Map.of();

    public CommerceState(Map<String, Object> data) { super(data); }

    public String string(String key) { return this.<String>value(key).orElse(""); }
    public CommerceCommand command() { return CommerceCommand.valueOf(string("command")); }
    public String endpoint() { return string("mcpEndpoint"); }
    public String cartId() { return string("cartId"); }
    public List<String> discoveredTools() {
        return this.<List<String>>value("discoveredTools").orElse(List.of());
    }
    public boolean supports(String toolName) { return discoveredTools().contains(toolName); }
    public CartStatus cartStatus() {
        String status = string("cartStatus");
        return status.isBlank() ? CartStatus.EMPTY : CartStatus.valueOf(status);
    }
    public CheckoutStatus checkoutStatus() {
        String status = string("checkoutStatus");
        return status.isBlank() ? CheckoutStatus.NONE : CheckoutStatus.valueOf(status);
    }
    public CommerceStateMachine.State businessState() {
        return new CommerceStateMachine.State(cartStatus(), checkoutStatus());
    }
    public int quantity() { return this.<Integer>value("quantity").orElse(1); }
    public List<Map<String, String>> searchResults() {
        return this.<List<Map<String, String>>>value("searchResults").orElse(List.of());
    }
}
