package dev.jdguo.market.command;

/**
 * Command words are ASCII. Player chat is Chinese and is not built from these words.
 */
public final class MarketCommandNames {
    public static final String ROOT = "market";
    public static final String LIST = "list";
    public static final String SHOP = "shop";
    public static final String BUY = "buy";
    public static final String CANCEL = "cancel";
    public static final String SEARCH = "search";
    public static final String ITEM = "item";
    public static final String PLAYER = "player";
    public static final String LAND = "land";
    public static final String MINE = "mine";
    public static final String INFO = "info";
    public static final String PRICE = "price";
    public static final String RELOAD = "reload";
    public static final String PARCEL = "parcel";
    public static final String CHUNK = "chunk";
    public static final String CHUNKS = "chunks";
    public static final String SERVER = "server";

    public static final String USAGE =
            "Usage: /market ui | offer <currency> <price|shop> [count] | list <price> [count] | list shop [count] | buy <id> | cancel <id> | search [page] | search item <item> [page] | search player <name> [page] | search land [page] | search mine [page] | info <id> | price | reload | land list parcel <price> | land list chunk <price> | land list chunks <price> <x1> <z1> <x2> <z2> | land server parcel <price> | land server chunk <price> | land server chunks <price> <x1> <z1> <x2> <z2> | land search [page]";

    public static final String[] LITERALS = {
            ROOT, LIST, SHOP, BUY, CANCEL, SEARCH, ITEM, PLAYER, LAND, MINE, INFO, PRICE, RELOAD,
            PARCEL, CHUNK, CHUNKS, SERVER
    };

    private MarketCommandNames() {
    }
}
