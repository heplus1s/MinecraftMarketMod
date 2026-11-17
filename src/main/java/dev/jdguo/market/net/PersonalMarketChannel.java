package dev.jdguo.market.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

/** Per-player one-use, expiring confirmations. Ledger remains the sole settlement authority. */
public final class PersonalMarketChannel {
    public static final Identifier CHANNEL = new Identifier("marketmod", "personal");
    private static final String API = "dev.jdguo.bank.api.PersonalMarketSnapshot";
    private static final PersonalConfirmations pending = new PersonalConfirmations();
    private PersonalMarketChannel() {}
    public static void disconnect(UUID player) { pending.remove(player); }
    private static Object call(String method, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        return Class.forName(API).getMethod(method, types).invoke(null, args);
    }
    private static boolean methodAvailable(String name, Class<?>... types) {
        try { Class.forName(API).getMethod(name,types);return true; } catch(ReflectiveOperationException|LinkageError e){return false;}
    }
    private static boolean paymentAvailable(){return methodAvailable("quotePayment",UUID.class,UUID.class,BigInteger.class,String.class)&&methodAvailable("buyQuotedPayment",UUID.class,UUID.class,BigInteger.class,String.class,String.class);}
    private static boolean validTender(String value){
        if(value.equals("note")||value.equals("diamond")||value.equals("copper"))return true;
        if(!value.startsWith("personal:"))return false;
        try {return UUID.fromString(value.substring(9)).toString().equals(value.substring(9));}catch(IllegalArgumentException e){return false;}
    }
    public static void act(ServerPlayerEntity player, String[] parts) {
        if (!ServerPlayNetworking.canSend(player, CHANNEL)) return;
        try {
            JsonObject message = new JsonObject();
            if (parts.length == 1 && "notes".equals(parts[0])) {
                message = JsonParser.parseString((String) call("catalog", new Class<?>[]{UUID.class}, player.getUuid())).getAsJsonObject();
                if(paymentAvailable())message.addProperty("paymentProtocol",1);
                try{message.addProperty("personalPayments",((Number)call("paymentProtocol",new Class<?>[]{})).intValue()>=2);}catch(ReflectiveOperationException ignored){}
            } else if ((parts.length == 3 || parts.length == 4) && "notequote".equals(parts[0]) && parts[2].matches("[1-9][0-9]{0,63}")) {
                pending.remove(player.getUuid());
                UUID coin = UUID.fromString(parts[1]); BigInteger amount = new BigInteger(parts[2]);
                String tender=parts.length==4?parts[3]:"note";
                if(!validTender(tender)){message.addProperty("error","不支持的付款币种。");send(player,message);return;}
                boolean legacy=!paymentAvailable();
                if(legacy&&!tender.equals("note")){message.addProperty("error","银行版本不支持此付款方式，请升级；不会改扣央行券。");send(player,message);return;}
                message = JsonParser.parseString((String) (legacy
                    ? call("quote", new Class<?>[]{UUID.class, UUID.class, BigInteger.class}, player.getUuid(), coin, amount)
                    : call("quotePayment",new Class<?>[]{UUID.class,UUID.class,BigInteger.class,String.class},player.getUuid(),coin,amount,tender))).getAsJsonObject();
                if(legacy){message.addProperty("tender",tender);message.addProperty("paymentAmount",message.has("maximum")?message.get("maximum").getAsString():"0");message.addProperty("change","0");}
                PersonalQuoteEnvelope.bindTender(message,tender);
                message.addProperty("kind", "quote"); message.addProperty("id", coin.toString()); message.addProperty("quantity", amount.toString());
                if (message.get("ok").getAsBoolean()) {
                    String token = pending.put(player.getUuid(), coin, amount, new BigDecimal(message.get("maximum").getAsString()),tender,message.toString(),legacy,System.currentTimeMillis()).token();
                    message.addProperty("token", token);
                }
            } else if (parts.length == 2 && "notebuy".equals(parts[0])) {
                PersonalConfirmations.Order order = pending.consume(player.getUuid(), parts[1], System.currentTimeMillis());
                if (order == null) {
                    message.addProperty("error", "确认已失效，请重新询价。");
                } else {
                    Object result = order.legacy()
                        ? call("buy", new Class<?>[]{UUID.class, UUID.class, BigInteger.class, BigDecimal.class}, player.getUuid(), order.coin(), order.quantity(), order.maximum())
                        : call("buyQuotedPayment",new Class<?>[]{UUID.class,UUID.class,BigInteger.class,String.class,String.class},player.getUuid(),order.coin(),order.quantity(),order.tender(),order.quote());
                    boolean success = (boolean) result.getClass().getMethod("success").invoke(result);
                    String code = String.valueOf(result.getClass().getMethod("code").invoke(result));
                    String detail = String.valueOf(result.getClass().getMethod("message").invoke(result));
                    message.addProperty("completed", true); message.addProperty("ok", success);
                    message.addProperty("message", success ? detail : "交易未完成（"+code+"），请刷新余额和卖盘后重试。");
                }
            } else return;
            send(player, message);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            JsonObject error = new JsonObject(); error.addProperty("error", "银行个人币接口不可用，请确认银行与市场模组均为配套新版。"); send(player,error);
        }
    }
    private static void send(ServerPlayerEntity player, JsonObject data) {
        String json=data.toString();
        if(json.length()>200000){data=new JsonObject();data.addProperty("error","币种目录过大，暂无法显示；请使用 /market note 命令。");json=data.toString();}
        var buf=PacketByteBufs.create();buf.writeString(json,262144);ServerPlayNetworking.send(player,CHANNEL,buf);
    }
}
