package com.example.commerce.graph;

import com.example.commerce.ucp.UcpGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DiscoveryNode {
    private final UcpGateway gateway;
    private final String merchant;

    public DiscoveryNode(UcpGateway gateway, @Value("${commerce.merchant-origin}") String merchant) {
        this.gateway = gateway;
        this.merchant = merchant;
    }

    public Map<String, Object> execute(CommerceState state) {
        if (!state.endpoint().isBlank() && !state.discoveredTools().isEmpty()) return Map.of(); // Cached in conversation; production: add TTL/invalidation.
        try {
            String endpoint = gateway.discover(merchant).endpoint();
            var tools = gateway.listTools(endpoint);
            if (tools.isEmpty()) throw new IllegalStateException("Nenhuma tool anunciada pela loja");
            return Map.of("mcpEndpoint", endpoint, "discoveredTools", tools);
        } catch (RuntimeException ex) {
            return Map.of("error", "UCP discovery indisponível", "reply", "Não consegui conectar à loja agora.");
        }
    }
}
