package dev.jdguo.market.net;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-only capabilities, bound to player, currency and exact quoted order. */
public final class PersonalConfirmations {
    public record Order(String token, UUID coin, BigInteger quantity, BigDecimal maximum, String tender, String quote, boolean legacy, long expires) {}
    private final Map<UUID,Order> orders=new HashMap<>();
    public Order put(UUID player,UUID coin,BigInteger quantity,BigDecimal maximum,long now){
        return put(player,coin,quantity,maximum,"note","",true,now);
    }
    public Order put(UUID player,UUID coin,BigInteger quantity,BigDecimal maximum,String tender,String quote,boolean legacy,long now){
        if(quantity.signum()<=0||maximum.signum()<=0)throw new IllegalArgumentException("nonpositive order");
        Order order=new Order(UUID.randomUUID().toString(),coin,quantity,maximum,tender,quote,legacy,now+30000);orders.put(player,order);return order;
    }
    public Order consume(UUID player,String token,long now){Order order=orders.remove(player);return order!=null&&order.token().equals(token)&&now<order.expires()?order:null;}
    public void remove(UUID player){orders.remove(player);}
}
