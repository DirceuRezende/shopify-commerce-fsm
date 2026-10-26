package com.example.commerce.graph;

import com.example.commerce.ucp.UcpGateway;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Component
public class SearchNode {
    private final UcpGateway gateway;
    public SearchNode(UcpGateway gateway) { this.gateway = gateway; }

    public Map<String, Object> execute(CommerceState state) {
        try {
            if (!state.supports("search_catalog")) throw new IllegalStateException("Tool search_catalog não anunciada");
            String query = state.string("query");
            if (query.isBlank()) throw new IllegalArgumentException("Informe o termo da busca.");
            List<UcpGateway.Product> products = gateway.search(state.endpoint(), query);
            List<Map<String, String>> results = products.stream().limit(10).map(p -> Map.of(
                "title", p.title(), "variantId", p.variantId(), "price", p.price())).toList();
            StringBuilder reply = new StringBuilder();
            if (results.isEmpty()) reply.append("Não encontrei produtos para: ").append(query);
            else {
                reply.append("Produtos encontrados:\n");
                IntStream.range(0, results.size()).forEach(i -> {
                    var product = results.get(i);
                    reply.append(i + 1).append(". ").append(product.get("title"))
                        .append(" — ").append(product.get("price"));
                    if (product.get("variantId").isBlank()) reply.append(" (variante a selecionar)");
                    reply.append("\n");
                });
                reply.append("Escolha um número para adicionar ao carrinho.");
            }
            return Map.of("searchResults", results, "reply", reply.toString());
        } catch (RuntimeException ex) {
            return Map.of("error", "SEARCH_FAILED", "reply", "Não foi possível pesquisar: " + ex.getMessage());
        }
    }
}
