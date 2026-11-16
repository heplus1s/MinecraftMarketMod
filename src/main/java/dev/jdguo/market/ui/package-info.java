/**
 * Reserved attachment point for a later chest menu.
 * <p>
 * This build does not register a screen handler and does not open a chest.
 * A later menu should call {@link dev.jdguo.market.ui.MarketChestUiHooks} on the
 * server thread and redraw from the snapshot it returns. Listing, escrow, and
 * settlement stay in the market service, so the menu must not move items or
 * savings itself.
 * <p>
 * The optional client screen belongs in MinecraftUIMod. It should speak the
 * channel registered by {@code dev.jdguo.market.net.MarketSync}. A vanilla client
 * never registers that channel, and the server does not send it unless the
 * client has announced the channel.
 */
package dev.jdguo.market.ui;
