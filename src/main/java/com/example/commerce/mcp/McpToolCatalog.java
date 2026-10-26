package com.example.commerce.mcp;

import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** One remotely configured UCP MCP server. Tool schemas and implementations are supplied by tools/list. */
@Component
public class McpToolCatalog {
    private final SyncMcpToolCallbackProvider provider;

    public McpToolCatalog(SyncMcpToolCallbackProvider provider) {
        this.provider = provider;
    }

    public List<String> names() {
        return Arrays.stream(provider.getToolCallbacks())
            .map(t -> t.getToolDefinition().name()).toList();
    }

    public ToolCallback[] allow(Set<String> allowed) {
        return Arrays.stream(provider.getToolCallbacks())
            .filter(t -> allowed.stream().anyMatch(name -> matches(t.getToolDefinition().name(), name)))
            .toArray(ToolCallback[]::new);
    }

    /** A single named client may still prefix tool names in Spring AI. */
    public static boolean matches(String available, String expected) {
        return available.equals(expected) || available.endsWith("_" + expected);
    }
}
