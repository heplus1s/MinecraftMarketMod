package dev.jdguo.market.outlet;

/** World anchor only; generated display entities are reconciled from this record. */
public record MarketOutlet(String name, String world, int x, int y, int z) {
    public static boolean validName(String name) { return name != null && name.matches("[a-zA-Z0-9_-]{1,32}"); }
    public boolean at(String world, int x, int y, int z) {
        return this.world.equals(world) && this.x == x && this.y == y && this.z == z;
    }
    public boolean inRange(String world, double x, double y, double z) {
        double dx = x - (this.x + .5), dy = y - (this.y + 1), dz = z - (this.z + .5);
        return this.world.equals(world) && dx * dx + dy * dy + dz * dz <= 36;
    }
}
