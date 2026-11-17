package dev.jdguo.market.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import java.util.ArrayList;
import java.util.List;

/** Read-only full, wrapped quote explanation; returning never submits payment. */
final class PersonalPaymentDetailsScreen extends Screen {
    private final Screen back;
    private final List<String> paragraphs;
    private List<OrderedText> lines=List.of();
    private int offset;
    PersonalPaymentDetailsScreen(Screen back,List<String> paragraphs){super(Text.literal("个人币结算明细"));this.back=back;this.paragraphs=List.copyOf(paragraphs);}
    @Override protected void init(){
        List<OrderedText> wrapped=new ArrayList<>();
        for(String p:paragraphs){wrapped.addAll(textRenderer.wrapLines(Text.literal(p),Math.max(80,width-32)));wrapped.add(Text.empty().asOrderedText());}
        lines=wrapped;offset=Math.min(offset,maxOffset());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回报价（不付款）"),b->close()).dimensions(Math.max(4,width/2-90),height-26,180,20).build());
    }
    private int capacity(){return Math.max(1,(height-72)/12);}
    private int maxOffset(){return Math.max(0,lines.size()-capacity());}
    @Override public boolean mouseScrolled(double x,double y,double amount){offset=Math.max(0,Math.min(maxOffset(),offset-(int)Math.signum(amount)*3));return true;}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        renderBackground(c);c.drawCenteredTextWithShadow(textRenderer,title,width/2,10,0xffffff);
        for(int i=0;i<capacity()&&i+offset<lines.size();i++)c.drawText(textRenderer,lines.get(i+offset),16,32+i*12,0xdde8f2,false);
        super.render(c,mx,my,delta);
    }
    @Override public void close(){if(client!=null)client.setScreen(back);}
    @Override public boolean shouldPause(){return false;}
}
