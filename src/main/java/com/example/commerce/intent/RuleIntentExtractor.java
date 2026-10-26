package com.example.commerce.intent;

import com.example.commerce.domain.CommerceCommand;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Predictable offline demo parser, intentionally limited. Use explicit REST commands for full determinism. */
@Component
@Profile("!ai")
public class RuleIntentExtractor implements IntentExtractor {
    private static final Pattern NUMBER = Pattern.compile("\\b(\\d+)\\b");

    @Override public IntentData extract(String message) {
        String normalized = Normalizer.normalize(message.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        String ref = ordinal(normalized);
        if (normalized.contains("remov") || normalized.contains("exclu") || normalized.contains("tirar"))
            return new IntentData(CommerceCommand.REMOVE_FROM_CART, "", ref, 1);
        if (normalized.contains("adicion") || normalized.contains("coloc") || normalized.contains("comprar este"))
            return new IntentData(CommerceCommand.ADD_TO_CART, "", ref, 1);
        if (normalized.contains("checkout") || normalized.contains("finaliz") || normalized.contains("fechar pedido"))
            return new IntentData(CommerceCommand.CREATE_CHECKOUT, "", "", 1);
        if (normalized.contains("carrinho"))
            return new IntentData(CommerceCommand.VIEW_CART, "", "", 1);
        if (normalized.contains("busc") || normalized.contains("procur") || normalized.contains("pesquis")
            || normalized.contains("quero") || normalized.contains("mostre") || normalized.contains("tenis"))
            return new IntentData(CommerceCommand.SEARCH, message, "", 1);
        return new IntentData(CommerceCommand.UNKNOWN, "", "", 1);
    }

    private String ordinal(String text) {
        if (text.contains("primeir")) return "1";
        if (text.contains("segund")) return "2";
        if (text.contains("terceir")) return "3";
        var matcher = NUMBER.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }
}
