package com.example.commerce.api;

import com.example.commerce.domain.CommerceCommand;
import com.example.commerce.graph.CommerceState;
import com.example.commerce.intent.IntentData;
import com.example.commerce.intent.IntentExtractor;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {
    private final CompiledGraph<CommerceState> graph;
    private final IntentExtractor extractor;
    private final ConcurrentHashMap<String, Object> localLocks = new ConcurrentHashMap<>();

    public ChatService(CompiledGraph<CommerceState> graph, IntentExtractor extractor) {
        this.graph = graph;
        this.extractor = extractor;
    }

    public ChatResponse chat(ChatRequest request) {
        if ((request.message() == null || request.message().isBlank()) && request.command() == null)
            throw new IllegalArgumentException("Informe message ou command");
        // A single JVM lock suffices for this demo; multi-instance deployments need distributed per-thread coordination.
        String key = request.conversationId();
        synchronized (localLocks.computeIfAbsent(key, unused -> new Object())) {
            return run(request);
        }
    }

    private ChatResponse run(ChatRequest request) {
        IntentData intent = request.command() != null
            ? new IntentData(request.command(), request.query(), request.productReference(), request.quantity())
            : extractor.extract(request.message());
        Map<String, Object> input = new HashMap<>();
        input.put("command", intent.command().name());
        input.put("query", intent.query());
        input.put("productReference", intent.productReference());
        input.put("quantity", intent.quantity());
        input.put("userMessage", request.message() == null ? "" : request.message());
        input.put("error", "");
        input.put("reply", ""); // Prevents prior-turn answers from leaking into new responses.
        try {
            var config = RunnableConfig.builder().threadId(request.conversationId()).build();
            CommerceState state = graph.invoke(GraphInput.args(input), config).orElseThrow();
            return new ChatResponse(
                request.conversationId(), intent.command(), state.string("reply"),
                state.cartStatus(), state.checkoutStatus(), state.cartId(),
                state.string("checkoutId"), state.string("checkoutUrl"),
                state.searchResults(), state.string("error")
            );
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Não foi possível executar o grafo para esta conversa", ex);
        }
    }
}
