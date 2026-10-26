package com.example.commerce.graph;

import com.example.commerce.mcp.McpToolCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** The single MCP client is bootstrapped from config; this node validates published tools and optional UCP discovery. */
@Component
public class DiscoveryNode {
    private final McpToolCatalog catalog;
    private final ObjectMapper mapper;
    private final String merchantOrigin;
    private final String baseUrl;
    private final String path;
    private final boolean verify;

    public DiscoveryNode(McpToolCatalog catalog, ObjectMapper mapper,
            @Value("${commerce.merchant-origin}") String merchantOrigin,
            @Value("${spring.ai.mcp.client.streamable-http.connections.shopify.url}") String baseUrl,
            @Value("${spring.ai.mcp.client.streamable-http.connections.shopify.endpoint}") String path,
            @Value("${commerce.verify-well-known:false}") boolean verify) {
        this.catalog = catalog;
        this.mapper = mapper;
        this.merchantOrigin = merchantOrigin.replaceAll("/+$", "");
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.path = path;
        this.verify = verify;
    }

    public Map<String, Object> execute(CommerceState state) {
        try {
            if (verify) checkWellKnown();
            var names = catalog.names(); // tools/list, not hard-coded @Tool declarations
            if (names.isEmpty()) throw new IllegalStateException("MCP tools/list retornou zero ferramentas");
            return Map.of("discoveredTools", names);
        } catch (Exception e) {
            return Map.of("error", "DISCOVERY_FAILED", "reply", "Falha ao descobrir UCP/MCP: " + e.getMessage());
        }
    }

    private void checkWellKnown() throws Exception {
        // Optional validation. Client endpoint is configured at boot; no runtime reconnect to arbitrary URLs.
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var req = HttpRequest.newBuilder(URI.create(merchantOrigin + "/.well-known/ucp"))
            .timeout(Duration.ofSeconds(10)).GET().build();
        var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
        var document = mapper.readTree(resp.body());
        var services = document.path("ucp").path("services").path("dev.ucp.shopping");
        if (!services.isArray()) throw new IllegalStateException("dev.ucp.shopping não publicado no well-known");
        String configured = baseUrl + path;
        boolean sameEndpoint = false;
        for (var service : services) {
            if ("mcp".equals(service.path("transport").asText())
                && configured.equals(service.path("endpoint").asText())) sameEndpoint = true;
        }
        if (!sameEndpoint) throw new IllegalStateException("Endpoint publicado difere do MCP configurado");
    }
}
