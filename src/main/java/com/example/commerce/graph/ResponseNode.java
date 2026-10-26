package com.example.commerce.graph;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class ResponseNode {
    public Map<String, Object> execute(CommerceState state) {
        if (state.string("reply").isBlank())
            return Map.of("reply", "Posso buscar produtos, consultar o carrinho ou preparar o checkout."
                + " Para ações, você também pode enviar um comando estruturado.");
        return Map.of();
    }
}
