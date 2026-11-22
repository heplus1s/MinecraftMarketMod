package dev.jdguo.market.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class GovernmentRepurchaseClientScreen extends Screen {
    private final Screen parent;
    private JsonArray rows = new JsonArray();
    private JsonObject quote;
    private String token = "", notice = "";
    private int page, pages = 1, total, scopePage, waiting;
    private boolean submitting, hasList;

    GovernmentRepurchaseClientScreen(Screen parent) {
        super(Text.literal("系统回购 · 玩家领地挂牌"));
        this.parent = parent;
    }

    void load() { request("list " + page, false); }

    void accept(JsonObject packet) {
        if (waiting == 0 && !Boolean.parseBoolean(text(packet, "open"))) return;
        waiting = 0;
        submitting = false;
        switch (text(packet, "type")) {
            case "list" -> {
                page = packet.get("page").getAsInt();
                pages = packet.get("pages").getAsInt();
                total = packet.get("count").getAsInt();
                rows = packet.getAsJsonArray("rows");
                hasList = true;
                quote = null;
                token = "";
            }
            case "preview" -> {
                quote = packet.getAsJsonObject("row");
                token = text(packet, "token");
                scopePage = 0;
                notice = "";
            }
            case "result" -> {
                notice = text(packet, "message");
                quote = null;
                token = "";
                load();
            }
            case "error" -> notice = text(packet, "message");
            default -> { return; }
        }
        rebuild();
    }

    private static String text(JsonObject row, String key) { return MarketScreen.text(row, key); }
    private void request(String action, boolean confirm) {
        if (waiting != 0) return;
        if (MarketUiClient.action("repurchaseui " + action)) {
            waiting = 120;
            submitting = confirm;
            if (confirm) token = "";
        } else notice = "未连接到市场服务端。";
        rebuild();
    }

    private void rebuild() { if (client != null) { clearChildren(); init(); } }
    private int panelWidth() { return Math.min(600, width - 28); }
    private int left() { return (width - panelWidth()) / 2; }
    private int rowHeight() { return Math.max(14, Math.min(24, (height - 99) / 8)); }
    private int chunksPerPage() { return Math.max(1, (height - 165) / 12) * 3; }

    @Override protected void init() {
        int x = left(), w = panelWidth(), footer = height - 28;
        if (height < 200 || w < 220) {
            button("关闭", x, footer, Math.max(55, w), this::close, !submitting);
            return;
        }
        if (quote != null) {
            var chunks = quote.getAsJsonArray("chunks");
            int capacity = chunksPerPage();
            int scopePages = Math.max(1, (chunks.size() + capacity - 1) / capacity);
            scopePage = Math.min(scopePage, scopePages - 1);
            button("上一页范围", x, footer - 22, 66, () -> { scopePage--; rebuild(); }, scopePage > 0 && waiting == 0);
            button("下一页范围", x + 71, footer - 22, 66, () -> { scopePage++; rebuild(); }, scopePage + 1 < scopePages && waiting == 0);
            button("返回列表", x, footer, 66, () -> {
                quote = null; token = ""; notice = ""; load();
            }, waiting == 0);
            button("确认系统回购", x + w - 104, footer, 104, () -> request("confirm " + token, true), waiting == 0 && !token.isBlank());
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i).getAsJsonObject();
            String label = text(row,"name") + "  ·  " + text(row,"scope") + "  ·  " + text(row,"seller")
                    + "  ·  " + text(row,"price") + " " + text(row,"currency");
            var select = button(textRenderer.trimToWidth(label, w - 14), x, 41 + i * rowHeight(), w, rowHeight() - 2,
                    () -> { notice = ""; request("preview " + text(row, "id"), false); }, waiting == 0);
            select.setTooltip(Tooltip.of(Text.literal(label + "\n" + text(row,"dimension") + "\n点击核对回购范围和总价，不扣款")));
        }
        button("上一页", x, footer, 52, () -> request("list " + (page - 1), false), waiting == 0 && page > 0);
        button("下一页", x + 56, footer, 52, () -> request("list " + (page + 1), false), waiting == 0 && page + 1 < pages);
        button("刷新", x + 112, footer, 44, this::load, waiting == 0);
        button("返回", x + w - 48, footer, 48, this::close, waiting == 0);
    }

    private ButtonWidget button(String label, int x, int y, int w, Runnable run, boolean enabled) {
        return button(label, x, y, w, 20, run, enabled);
    }

    private ButtonWidget button(String label, int x, int y, int w, int h, Runnable run, boolean enabled) {
        var button = addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> run.run()).dimensions(x,y,w,h).build());
        button.active = enabled;
        return button;
    }

    @Override public void tick() {
        if (waiting > 0 && --waiting == 0) {
            notice = submitting ? "未收到结算结果，请先核对交易记录，不要重复提交。" : "未收到服务器响应，可刷新重试。";
            submitting = false;
            rebuild();
        }
    }

    @Override public void close() { if (!submitting && client != null) client.setScreen(parent); }

    @Override public void render(DrawContext c,int mx,int my,float delta) {
        renderBackground(c);
        int x = left(), w = panelWidth();
        c.drawCenteredTextWithShadow(textRenderer,title,width/2,10,0xfff2d288);
        if (height < 200 || w < 220) {
            c.drawCenteredTextWithShadow(textRenderer,Text.literal("请调小界面缩放或扩大窗口"),width/2,32,0xffffff);
        } else if (quote == null) {
            c.drawTextWithShadow(textRenderer, (page+1) + "/" + pages + " 页 · " + total + " 笔 · 仅玩家以央行券挂牌的领地", x, 25, 0xffb8c9dd);
            if (rows.isEmpty() && waiting == 0 && hasList) c.drawTextWithShadow(textRenderer, "暂无可回购挂牌", x+4, 50, 0xffffff);
        } else {
            line(c, "宗地："+text(quote,"name")+"  ·  "+text(quote,"scope"), x, 26);
            line(c, "收款卖方："+text(quote,"seller")+"  ·  维度："+text(quote,"dimension"), x, 39);
            line(c, "回购总价："+text(quote,"price")+" "+text(quote,"currency"), x, 52);
            line(c, "由地产账户付款，过户至服务器用地；确认前不会扣款。", x, 65);
            line(c, "挂牌编号："+text(quote,"id"), x, 78);
            var chunks = quote.getAsJsonArray("chunks");
            int capacity = chunksPerPage(), start = scopePage * capacity;
            line(c,"实际区块 X, Z · "+(scopePage+1)+"/"+Math.max(1,(chunks.size()+capacity-1)/capacity),x,91);
            for(int i = start; i < Math.min(start+capacity,chunks.size()); i++) {
                int j = i-start;
                c.drawTextWithShadow(textRenderer,chunks.get(i).getAsString(),x+(j%3)*(w/3),104+(j/3)*12,0xffd3e1ed);
            }
        }
        if (height >= 200 && w >= 220) {
            String status = waiting > 0 && notice.isBlank() ? (submitting?"正在核对并提交回购…":"正在向服务器读取…") : notice;
            if (!status.isBlank()) c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(status,w),x,height-(quote == null ? 41 : 63),0xfff2d288);
        }
        super.render(c,mx,my,delta);
    }

    private void line(DrawContext c,String text,int x,int y) {
        c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(text,panelWidth()),x,y,0xffedf3fb);
    }
}
