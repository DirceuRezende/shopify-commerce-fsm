package com.example.commerce.mcp;

import com.example.commerce.graph.CommerceState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Set;

@Component
public class ScopedTools {
    private final McpToolCatalog catalog;
    private final ObjectMapper mapper;
    private final String profile;

    public ScopedTools(McpToolCatalog catalog, ObjectMapper mapper,
                       @Value("${commerce.ucp-agent-profile}") String profile) {
        this.catalog = catalog;
        this.mapper = mapper;
        this.profile = profile;
    }

    public ToolCallback[] forNode(Set<String> names, CommerceState state, InvocationLog log) {
        ToolCallback[] remote = catalog.allow(names);
        if (remote.length == 0) throw new IllegalStateException("Nenhuma tool requerida disponível no MCP: " + names);
        return Arrays.stream(remote)
            .map(t -> (ToolCallback) new GuardedMcpTool(t, state, log, mapper, profile, names))
            .toArray(ToolCallback[]::new);
    }
}
