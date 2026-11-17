package dev.jdguo.market.net;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Binds exact item, side, quantity and total to one player and one use. */
public final class SystemShopConfirmations {
    public record Order(String token, String item, int count, boolean buying, BigDecimal total, long expires) {}
    private final Map<UUID,Order> pending=new HashMap<>();
    public Order put(UUID player,String item,int count,boolean buying,BigDecimal total,long now) {
        if(player==null||item==null||item.isBlank()||count<1||count>2304||total==null||total.signum()<=0)throw new IllegalArgumentException();
        var order=new Order(UUID.randomUUID().toString(),item,count,buying,total,now+30000);
        pending.put(player,order);return order;
    }
    public Order consume(UUID player,String token,long now) {
        var order=pending.remove(player);
        return order!=null && order.token().equals(token) && now<=order.expires()?order:null;
    }
    public void remove(UUID player){pending.remove(player);}
}
