package dev.jdguo.market.outlet;

/** World anchor only; generated display entities are reconciled from this record. */
public record MarketOutlet(String name, String world, int x, int y, int z, int facing) {
    public MarketOutlet(String name, String world, int x, int y, int z) { this(name, world, x, y, z, 0); }
    public MarketOutlet rotated(int facing) { return new MarketOutlet(name, world, x, y, z, CounterRotation.normalize(facing)); }

    public static boolean validName(String name) { return name != null && name.matches("[\\p{L}\\p{N}_ -]{1,32}") && name.equals(name.trim()); }
    public boolean at(String world, int x, int y, int z) {
        return this.world.equals(world) && this.x == x && this.y == y && this.z == z;
    }
    public boolean inRange(String world, double x, double y, double z) {
        double dx = x - (this.x + .5), dy = y - (this.y + 1), dz = z - (this.z + .5);
        return this.world.equals(world) && dx * dx + dy * dy + dz * dz <= 36;
    }
}
