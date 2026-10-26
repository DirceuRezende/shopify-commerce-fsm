package com.example.commerce.config;

import com.example.commerce.domain.CommerceStateMachine;
import com.example.commerce.graph.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MongoDBSaver;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Configuration
public class CommerceGraphConfiguration {
    @Bean
    CommerceStateMachine commerceStateMachine() { return new CommerceStateMachine(); }

    @Bean(destroyMethod = "close")
    MongoClient mongoClient(@Value("${commerce.mongo-uri}") String uri) {
        return MongoClients.create(uri);
    }

    @Bean
    MongoDBSaver mongoDBSaver(MongoClient client, @Value("${commerce.mongo-database}") String database) {
        return MongoDBSaver.builder()
            .mongoClient(client)
            .databaseName(database)
            .stateSerializer(new ObjectStreamStateSerializer<>(CommerceState::new))
            .build();
    }

    @Bean
    CompiledGraph<CommerceState> commerceGraph(
        DiscoveryNode discovery, SearchNode search, CartNode cart, CheckoutNode checkout,
        ResponseNode respond, MongoDBSaver saver
    ) throws Exception {
        // No SupervisorNode. An explicit, finite command enum controls the only conditional edge.
        return new StateGraph<>(CommerceState.SCHEMA,
                new ObjectStreamStateSerializer<>(CommerceState::new))
            .addNode("discovery", node_async(discovery::execute))
            .addNode("search", node_async(search::execute))
            .addNode("cart", node_async(cart::execute))
            .addNode("checkout", node_async(checkout::execute))
            .addNode("respond", node_async(respond::execute))
            .addEdge(START, "discovery")
            .addConditionalEdges("discovery",
                edge_async(state -> {
                    if (!state.string("error").isBlank()) return "respond";
                    return switch (state.command()) {
                        case SEARCH -> "search";
                        case ADD_TO_CART, REMOVE_FROM_CART, VIEW_CART -> "cart";
                        case CREATE_CHECKOUT -> "checkout";
                        case UNKNOWN -> "respond";
                    };
                }),
                EdgeMappings.builder()
                    .to("search").to("cart").to("checkout").to("respond")
                    .build())
            .addEdge("search", "respond")
            .addEdge("cart", "respond")
            .addEdge("checkout", "respond")
            .addEdge("respond", END)
            .compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .recursionLimit(12)
                .build());
    }
}
