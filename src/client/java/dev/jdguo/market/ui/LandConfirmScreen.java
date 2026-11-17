package dev.jdguo.market.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jdguo.market.api.LandUiChannel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Native land checkout/listing without switching to a container screen. */
public final class LandConfirmScreen extends Screen {
    private final JsonObject data;
    private final Screen parent;
    private final boolean selling;
    private String currency;
    private TextFieldWidget price;
    private ButtonWidget confirm;
    private boolean submitted;
    private String message = "";
    private int x, top, w;
    public LandConfirmScreen(JsonObject data, Screen parent) {
        super(Text.literal("sell".equals(text(data, "action")) ? "出售领地" : "确认购买领地"));
        this.data = data; this.parent = parent; this.selling = "sell".equals(text(data, "action")); this.currency = text(data, "currency");
    }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LandUiChannel.OPEN, (client, handler, buf, sender) -> {
            String json = buf.readString(32767);
            client.execute(() -> { try { client.setScreen(new LandConfirmScreen(JsonParser.parseString(json).getAsJsonObject(), client.currentScreen)); } catch (RuntimeException ignored) {} });
        });
        ClientPlayNetworking.registerGlobalReceiver(LandUiChannel.RESULT, (client, handler, buf, sender) -> {
            String json = buf.readString(4096);
            client.execute(() -> { try {
                if (client.currentScreen instanceof LandConfirmScreen screen) screen.message = text(JsonParser.parseString(json).getAsJsonObject(), "message");
            } catch (RuntimeException ignored) {} });
        });
    }
    @Override protected void init() {
        w = Math.min(330, width - 24); x = (width - w) / 2; top = Math.max(6, (height - 204) / 2);
        String previous = price == null ? text(data, "total") : price.getText();
        price = new TextFieldWidget(textRenderer, x, top + 66, w, 20, Text.literal("整笔总价"));
        price.setMaxLength(19); price.setText(previous); price.setEditable(selling && !submitted); addDrawableChild(price);
        ButtonWidget currencyButton = addDrawableChild(ButtonWidget.builder(Text.literal(currencyLabel()), button -> {
            var options = data.getAsJsonArray("currencies");
            for (int i = 0; i < options.size(); i++) {
                if (text(options.get(i).getAsJsonObject(), "id").equals(currency)) {
                    currency = text(options.get((i + 1) % options.size()).getAsJsonObject(), "id"); break;
                }
            }
            button.setMessage(Text.literal(currencyLabel()));
        }).dimensions(x, top + 102, w, 20).build());
        currencyButton.active = selling && !submitted && data.has("currencies") && data.getAsJsonArray("currencies").size() > 1;
        confirm = addDrawableChild(ButtonWidget.builder(Text.literal(selling ? "确认挂牌" : "确认购买"), button -> {
            if (submitted) return;
            if (selling) {
                try { if (Long.parseLong(price.getText()) <= 0) throw new NumberFormatException(); }
                catch (NumberFormatException bad) { message = "请输入正整数总价"; return; }
            }
            JsonObject input = new JsonObject(); input.addProperty("action", "confirm"); input.addProperty("token", text(data, "token"));
            input.addProperty("currency", currency); input.addProperty("price", price.getText());
            if (send(input)) { submitted = true; button.active = false; currencyButton.active = false; price.setEditable(false); message = "已提交，等待服务器确认；请勿重复付款"; }
            else message = "服务器未提供确认接口，未提交";
        }).dimensions(x, top + 154, w / 2 - 2, 20).build());
        confirm.active = !submitted;
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close()).dimensions(x + w / 2 + 2, top + 154, w / 2 - 2, 20).build());
    }
    private String currencyLabel() {
        String label = text(data, "currencyName");
        if (data.has("currencies")) for (var row : data.getAsJsonArray("currencies")) {
            if (text(row.getAsJsonObject(), "id").equals(currency)) { label = text(row.getAsJsonObject(), "label"); break; }
        }
        if (currency.equals("note") && (label.isBlank() || label.equals("note") || label.equals("银行券"))) label = "央行券";
        if (label.isBlank() || label.equals(currency)) label = currency.startsWith("personal:") ? "个人币（名称暂不可用）" : currency;
        return label + (currency.startsWith("personal:") ? "（个人币）" : currency.equals("note") ? "（央行）" : "");
    }
    private static String text(JsonObject data, String key) { try { return data.has(key) ? data.get(key).getAsString() : ""; } catch (RuntimeException bad) { return ""; } }
    private boolean send(JsonObject input) {
        if (!ClientPlayNetworking.canSend(LandUiChannel.ACTION)) return false;
        var packet = PacketByteBufs.create(); packet.writeString(input.toString(), 4096); ClientPlayNetworking.send(LandUiChannel.ACTION, packet); return true;
    }
    @Override public void close() {
        if (!submitted) { JsonObject input = new JsonObject(); input.addProperty("action", "cancel"); input.addProperty("token", text(data, "token")); send(input); }
        client.setScreen(parent);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawTextWithShadow(textRenderer, title, x, top, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "范围：" + text(data, "chunks") + " 个区块", x, top + 20, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(text(data, "dimension"), w), x, top + 34, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, selling ? "整笔挂牌总价" : "应付总价（银行储蓄扣款）", x, top + 54, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, selling ? "计价币种（点击切换）" : "支付币种（由挂牌或管理员指定）", x, top + 90, 0xFFFFFF);
        String help = selling ? "仅登记选中范围；成交时重新核验所有权。" : "按所示币种支付，不自动换汇。价格变化须重新确认。";
        context.drawTextWrapped(textRenderer, Text.literal(help), x, top + 130, w, 0xBBBBBB);
        context.drawTextWrapped(textRenderer, Text.literal(message), x, top + 180, w, 0xFFE080);
        super.render(context, mouseX, mouseY, delta);
    }
}
