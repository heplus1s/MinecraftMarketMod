package dev.jdguo.market.integrate;

import dev.jdguo.market.config.MarketConfig;
import java.util.ArrayList;
import java.util.List;

/** Read-only discovery of the bank's public currency registry. Never edits its ledger. */
public final class MarketCurrencies {
    public record Option(String id, String label) {}
    private MarketCurrencies() {}
    public static List<Option> options() {
        try {
            Class<?> runtime = Class.forName("dev.jdguo.bank.persistence.BankRuntime");
            Object books = runtime.getMethod("books").invoke(null);
            Iterable<?> defs = (Iterable<?>) books.getClass().getMethod("currencies").invoke(books);
            List<Option> result = new ArrayList<>();
            for (Object def : defs) {
                String id = (String) def.getClass().getMethod("id").invoke(def);
                String label = (String) def.getClass().getMethod("name").invoke(def);
                if (validId(id)) result.add(new Option(id, label == null || label.isBlank() ? fallbackLabel(id) : label));
            }
            return withPersonal(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            // Keep standalone listing usable; checkout still refuses without a bank.
            String id = MarketConfig.get().currency();
            return List.of(new Option(id, fallbackLabel(id)));
        }
    }
    public static String fallbackLabel(String id) {
        if (id == null) return "";
        return switch (id) {
            case "note" -> "央行券";
            case "diamond" -> "钻石";
            case "copper" -> "铜锭";
            default -> id;
        };
    }

    private static List<Option> withPersonal(List<Option> base) {
        List<Option> result = new ArrayList<>(base);
        try {
            Class<?> runtime = Class.forName("dev.jdguo.bank.persistence.BankRuntime");
            Object books = runtime.getMethod("books").invoke(null);
            Iterable<?> notes = (Iterable<?>) books.getClass().getMethod("personalNotes").invoke(books);
            for (Object note : notes) {
                Object id = note.getClass().getMethod("id").invoke(note);
                String name = String.valueOf(note.getClass().getMethod("name").invoke(note));
                result.add(new Option("personal:" + id, name));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { /* absent bank or old API */ }
        return List.copyOf(result);
    }
    public static List<Option> landOptions() { return options(); }
    public static boolean acceptsLand(String id) { return accepts(id); }
    /** IDs win; names must uniquely identify a currency, never silently choose a duplicate. */
    public static String resolve(String raw) {
        if (raw == null) return null;
        List<Option> options = options();
        for (Option option : options) if (option.id().equals(raw)) return option.id();
        List<Option> named = options.stream().filter(o -> o.label().equals(raw)).toList();
        return named.size() == 1 ? named.get(0).id() : null;
    }

    public static boolean validId(String id) {
        return dev.jdguo.market.listing.ListingTerms.validCurrencyId(id);
    }
    public static boolean accepts(String id) {
        return validId(id) && options().stream().anyMatch(option -> option.id().equals(id));
    }
}
