package dev.jdguo.market.outlet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MarketOutletTest {
 @Test void identifierCannotInjectTagsOrFormatting() {
  assertTrue(MarketOutlet.validName("central-market_1"));
  for(String n:new String[]{"", "bad name", "§a", "a:b", "a".repeat(33)}) assertFalse(MarketOutlet.validName(n));
  assertFalse(MarketOutlet.validName(null));
 }
 @Test void anchorsAndReachAreDimensionScoped() {
  MarketOutlet p=new MarketOutlet("main","minecraft:overworld",-16,64,0);
  assertTrue(p.at("minecraft:overworld",-16,64,0));
  assertFalse(p.at("minecraft:the_nether",-16,64,0));
  assertTrue(p.inRange("minecraft:overworld",-15.5,65,.5));
  assertFalse(p.inRange("minecraft:the_nether",-15.5,65,.5));
  assertFalse(p.inRange("minecraft:overworld",100,65,.5));
 }
}
