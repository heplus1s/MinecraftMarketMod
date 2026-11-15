package dev.jdguo.market.integrate;

import dev.jdguo.market.config.MarketConfig;
import dev.jdguo.market.message.MarketMessages;
import java.util.ArrayList;
import java.util.List;

/** Read-only discovery of the bank's public currency registry. Never edits its ledger. */
public final class MarketCurrencies {
    public record Option(String id, String label) {}
    private MarketCurrencies() {}
    public static List<Option> options() {
        try {
            Class<?> runtime = Class.forName("dev.jdguo.bank.persistence.BankRuntime");
            Object config = runtime.getField("config").get(null);
            Iterable<?> defs = (Iterable<?>) config.getClass().getMethod("currencies").invoke(config);
            List<Option> result = new ArrayList<>();
            for (Object def : defs) {
                String id = (String) def.getClass().getMethod("id").invoke(def);
                String label = (String) def.getClass().getMethod("name").invoke(def);
                if (validId(id)) result.add(new Option(id, label == null ? id : label));
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | LinkageError exception) {
            // Keep standalone listing usable; checkout still refuses without a bank.
            String id = MarketConfig.get().currency();
            return List.of(new Option(id, MarketMessages.currency(id)));
        }
    }
    public static boolean validId(String id) {
        return dev.jdguo.market.listing.ListingTerms.validCurrencyId(id);
    }
    public static boolean accepts(String id) {
        return validId(id) && options().stream().anyMatch(option -> option.id().equals(id));
    }
}
