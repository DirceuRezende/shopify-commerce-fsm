package com.example.commerce.intent;

import com.example.commerce.domain.CommerceCommand;

/** Command proposal only. Never contains a business state or payment authorization. */
public record IntentData(CommerceCommand command, String query, String productReference, Integer quantity) {
    public IntentData {
        if (command == null) command = CommerceCommand.UNKNOWN;
        if (query == null) query = "";
        if (productReference == null) productReference = "";
        if (quantity == null) quantity = 1;
    }
}
