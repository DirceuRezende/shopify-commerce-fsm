package com.example.commerce.mcp;

import java.util.ArrayList;
import java.util.List;

/** Request-local, never a singleton: one log per executing graph node. */
public final class InvocationLog {
    public record Entry(String tool, String rawResult) {}
    private final List<Entry> entries = new ArrayList<>();
    public void record(String name, String output) { entries.add(new Entry(name, output)); }
    public List<Entry> entries() { return List.copyOf(entries); }
    public Entry last(String tool) {
        for (int i = entries.size() - 1; i >= 0; i--)
            if (McpToolCatalog.matches(entries.get(i).tool(), tool)) return entries.get(i);
        return null;
    }
}
