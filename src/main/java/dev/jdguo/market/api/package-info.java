/**
 * Stable calls for neighboring mods. Nothing in this package opens a screen.
 * <p>
 * The shop, when it exists, should install {@link dev.jdguo.market.api.ShopPriceApi}
 * or expose {@code dev.jdguo.shop.api.ShopPrices.priceEach(String, String)}.
 * A missing price does not block a custom price.
 * <p>
 * The bank does not yet move savings from one player to another, and it does not
 * yet keep the 土地出让账户. {@link dev.jdguo.market.api.BankSettlement} and
 * {@link dev.jdguo.market.api.LandGrantAccount} are the hooks it should fill.
 * {@link dev.jdguo.market.api.BankHookSpec} names the class and methods.
 */
package dev.jdguo.market.api;
