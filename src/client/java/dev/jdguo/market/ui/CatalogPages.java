package dev.jdguo.market.ui;

import com.google.gson.JsonObject;
import java.util.*;

/** Apply complete snapshots only; never mix currencies or generations. */
final class CatalogPages {
    record Snapshot(String currency, List<JsonObject> items) {}
    private long generation = -1;
    private String currency = "";
    private int total;
    private final Map<Integer,List<JsonObject>> pages = new HashMap<>();
    Snapshot accept(JsonObject packet) {
        try {
            long id=packet.get("requestId").getAsLong();
            int page=packet.get("page").getAsInt(), count=packet.get("pages").getAsInt();
            String denomination=packet.get("currency").getAsString();
            if(id<generation||count<1||count>1024||page<0||page>=count||!packet.has("items")||!packet.get("items").isJsonArray())return null;
            if(id>generation){generation=id;currency=denomination;total=count;pages.clear();}
            if(!currency.equals(denomination)||total!=count)return null;
            List<JsonObject> items=new ArrayList<>();
            for(var item:packet.getAsJsonArray("items"))if(item.isJsonObject())items.add(item.getAsJsonObject());
            pages.put(page,items);
            if(pages.size()!=total)return null;
            List<JsonObject> result=new ArrayList<>();for(int i=0;i<total;i++)result.addAll(pages.get(i));
            return new Snapshot(currency,List.copyOf(result));
        }catch(RuntimeException ex){return null;}
    }
}
