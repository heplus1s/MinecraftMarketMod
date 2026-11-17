package dev.jdguo.market.net;

import com.google.gson.JsonObject;

/** Failure replies may carry only ok/code; never mistake them for interface failures. */
final class PersonalQuoteEnvelope {
    private PersonalQuoteEnvelope() {}
    static void bindTender(JsonObject quote,String requested) {
        boolean success=quote.has("ok")&&quote.get("ok").getAsBoolean();
        if(success&&(!quote.has("tender")||!requested.equals(quote.get("tender").getAsString())))
            throw new IllegalStateException("quote tender mismatch");
        // Requested tender is server-derived, not accepted from a client quote payload.
        if(!success)quote.addProperty("tender",requested);
    }
}
