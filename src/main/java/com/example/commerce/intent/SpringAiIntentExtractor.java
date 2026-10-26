package com.example.commerce.intent;

import com.example.commerce.domain.CommerceCommand;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** LLM interprets text; this is NOT a supervisor and cannot change domain statuses. */
@Component
@Profile("ai")
public class SpringAiIntentExtractor implements IntentExtractor {
    private final ChatClient client;
    public SpringAiIntentExtractor(ChatModel model) { this.client = ChatClient.create(model); }

    @Override public IntentData extract(String message) {
        try {
            IntentData result = client.prompt()
                .system("""
                    Classifique a intenção de um comprador de uma loja em UMA das categorias:
                    SEARCH, ADD_TO_CART, REMOVE_FROM_CART, VIEW_CART, CREATE_CHECKOUT, UNKNOWN.
                    Extraia query para SEARCH e productReference para produtos.
                    Se mencionar 'primeiro', 'segundo', 'terceiro', use 1, 2 ou 3 como productReference.
                    Nunca invente ProductVariant GIDs. Se não for claro, use UNKNOWN.
                    Extraia quantity como número inteiro positivo (padrão 1).
                    Nunca defina estado do carrinho, do checkout nem execute tools.
                    Não infira que o usuário autorizou pagamento.
                    """)
                .user(message)
                .call()
                .entity(IntentData.class);
            if (result == null || result.command() == null) return unknown();
            // Fail closed on ambiguous or conflicting output.
            if (result.quantity() < 1 || result.quantity() > 99) return unknown();
            return result;
        } catch (RuntimeException ex) {
            return unknown();
        }
    }

    private IntentData unknown() { return new IntentData(CommerceCommand.UNKNOWN, "", "", 1); }
}
