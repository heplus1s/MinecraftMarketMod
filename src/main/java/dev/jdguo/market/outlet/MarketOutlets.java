package dev.jdguo.market.outlet;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.jdguo.market.ui.MarketChestScreen;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.PersistentState;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

/** Vanilla entities/menus only. No custom client packet or new currency path. */
public final class MarketOutlets extends PersistentState {
    private static final String TAG = "marketmod_outlet";
    private static final String NAME = "marketmod_outlet_name:";
    private final Map<String, MarketOutlet> outlets = new LinkedHashMap<>();
    public static MarketOutlets get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(MarketOutlets::read, MarketOutlets::new, "marketmod_outlets");
    }
    private static MarketOutlets read(NbtCompound nbt) {
        MarketOutlets state = new MarketOutlets();
        NbtList list = nbt.getList("outlets", NbtElement.COMPOUND_TYPE);
        for (int i=0; i<list.size(); i++) {
            NbtCompound v = list.getCompound(i);
            String name = v.getString("name");
            if (MarketOutlet.validName(name)) state.outlets.put(name, new MarketOutlet(name,v.getString("world"),v.getInt("x"),v.getInt("y"),v.getInt("z")));
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (MarketOutlet p : outlets.values()) {
            NbtCompound v = new NbtCompound(); v.putString("name",p.name()); v.putString("world",p.world());
            v.putInt("x",p.x());v.putInt("y",p.y());v.putInt("z",p.z());list.add(v);
        }
        nbt.put("outlets",list);return nbt;
    }
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment) -> dispatcher.register(literal("market")
            .then(literal("outlet").requires(s -> s.hasPermissionLevel(2))
                .then(literal("set").then(argument("name", StringArgumentType.word()).executes(c -> set(c.getSource(),StringArgumentType.getString(c,"name")))))
                .then(literal("remove").then(argument("name",StringArgumentType.word()).executes(c -> remove(c.getSource(),StringArgumentType.getString(c,"name")))))
                .then(literal("list").executes(c -> { for (MarketOutlet p:get(c.getSource().getServer()).outlets.values()) c.getSource().sendFeedback(() -> Text.literal(p.name()+" · "+p.world()+" "+p.x()+" "+p.y()+" "+p.z()),false);return 1; })) )));
        UseBlockCallback.EVENT.register((player,world,hand,hit) -> {
            if (!(player instanceof ServerPlayerEntity p) || hand != Hand.MAIN_HAND || p.isSpectator()) return ActionResult.PASS;
            var pos=hit.getBlockPos(); String dim=world.getRegistryKey().getValue().toString();
            for (MarketOutlet point:get(p.getServer()).outlets.values()) if(point.at(dim,pos.getX(),pos.getY(),pos.getZ()) && point.inRange(dim,p.getX(),p.getY(),p.getZ())) {
                MarketChestScreen.open(p); return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit) -> {
            if (!(player instanceof ServerPlayerEntity p) || hand != Hand.MAIN_HAND || p.isSpectator() || !entity.getCommandTags().contains(TAG)) return ActionResult.PASS;
            for(MarketOutlet point:get(p.getServer()).outlets.values()) if(entity.getCommandTags().contains(NAME+point.name()) && point.inRange(world.getRegistryKey().getValue().toString(),p.getX(),p.getY(),p.getZ())) {
                MarketChestScreen.open(p); return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if(server.getTicks()%20==0) get(server).reconcile(server); });
    }
    private static int set(ServerCommandSource source,String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p=source.getPlayerOrThrow();
        if(!MarketOutlet.validName(name)) {source.sendError(Text.literal("网点名称须为1–32位字母、数字、下划线或连字符"));return 0;}
        HitResult hit=p.raycast(5,0,false);
        if(!(hit instanceof BlockHitResult block) || hit.getType()!=HitResult.Type.BLOCK) {source.sendError(Text.literal("请看准柜台下方的实体方块"));return 0;}
        var pos=block.getBlockPos(); String dim=p.getWorld().getRegistryKey().getValue().toString();
        MarketOutlets state=get(source.getServer());
        for (Entity entity : p.getServerWorld().iterateEntities()) {
            boolean otherCounter = entity.getCommandTags().contains("bankmod_outlet") || entity.getCommandTags().contains("shopmod_outlet");
            if (otherCounter && Math.abs(entity.getX() - (pos.getX()+.5)) < 1 && Math.abs(entity.getZ() - (pos.getZ()+.5)) < 1 && Math.abs(entity.getY() - (pos.getY()+1)) < 2) {
                source.sendError(Text.literal("此位置已有银行或商店柜台，请选择其他位置")); return 0;
            }
        }
        for(MarketOutlet old:state.outlets.values()) if(!old.name().equals(name) && old.at(dim,pos.getX(),pos.getY(),pos.getZ())) {source.sendError(Text.literal("此位置已有市场网点"));return 0;}
        state.clearLoaded(source.getServer(),name);
        state.outlets.put(name,new MarketOutlet(name,dim,pos.getX(),pos.getY(),pos.getZ()));state.markDirty();
        state.reconcile(source.getServer()); source.sendFeedback(() -> Text.literal("已设置市场柜台 "+name+"；右键打开挂牌交易"),true);return 1;
    }
    private static int remove(ServerCommandSource source,String name) {
        MarketOutlets state=get(source.getServer());
        if(state.outlets.remove(name)==null) {source.sendError(Text.literal("没有这个市场网点"));return 0;}
        state.markDirty();state.clearLoaded(source.getServer(),name);source.sendFeedback(() -> Text.literal("已移除市场柜台 "+name),true);return 1;
    }
    private void clearLoaded(MinecraftServer server,String name) {
        for(ServerWorld world:server.getWorlds()) { List<Entity> remove=new ArrayList<>();
            for(Entity e:world.iterateEntities()) if(e.getCommandTags().contains(TAG)&&e.getCommandTags().contains(NAME+name)) remove.add(e);
            remove.forEach(Entity::discard);
        }
    }
    private void reconcile(MinecraftServer server) {
        for(ServerWorld world:server.getWorlds()) {
            String dim=world.getRegistryKey().getValue().toString();
            Map<String,List<Entity>> found=new HashMap<>();List<Entity> orphan=new ArrayList<>();
            for(Entity e:world.iterateEntities()) if(e.getCommandTags().contains(TAG)) {
                String key=e.getCommandTags().stream().filter(t->t.startsWith(NAME)).map(t->t.substring(NAME.length())).findFirst().orElse("");
                MarketOutlet p=outlets.get(key);
                if(p==null || !p.world().equals(dim) || Math.abs(e.getX()-(p.x()+.5))>.01 || Math.abs(e.getZ()-(p.z()+.5))>.01
                        || Math.abs(e.getY()-(p.y()+(e instanceof DisplayEntity.TextDisplayEntity ? 3 : 1)))>.01) orphan.add(e);
                else found.computeIfAbsent(key,k->new ArrayList<>()).add(e);
            }
            orphan.forEach(Entity::discard);
            for(MarketOutlet p:outlets.values()) if(p.world().equals(dim)&&world.isChunkLoaded(p.x()>>4,p.z()>>4)) {
                List<Entity> parts=found.getOrDefault(p.name(),List.of());
                if(parts.stream().filter(e->e instanceof InteractionEntity).count()==1 && parts.stream().filter(e->e instanceof DisplayEntity.TextDisplayEntity).count()==1 && parts.stream().filter(e->e instanceof DisplayEntity.BlockDisplayEntity).count()==3) continue;
                parts.forEach(Entity::discard); spawn(world,p);
            }
        }
    }
    private static void tag(Entity e,MarketOutlet p) {e.addCommandTag(TAG);e.addCommandTag(NAME+p.name());e.setInvulnerable(true);e.setSilent(true);e.setNoGravity(true);}
    private static void spawn(ServerWorld world,MarketOutlet p) {
        InteractionEntity hit=new InteractionEntity(EntityType.INTERACTION,world);tag(hit,p);
        hit.refreshPositionAndAngles(p.x()+.5,p.y()+1,p.z()+.5,0,0);
        NbtCompound n=new NbtCompound();hit.writeNbt(n);n.putFloat("width",1.6f);n.putFloat("height",1.1f);n.putBoolean("response",true);hit.readNbt(n);world.spawnEntity(hit);
        part(world,p,Blocks.DARK_OAK_PLANKS,-.75f,0,-.4f,1.5f,.9f,.8f);
        part(world,p,Blocks.SMOOTH_QUARTZ,-.8f,.9f,-.45f,1.6f,.1f,.9f);
        part(world,p,Blocks.EMERALD_BLOCK,-.8f,1f,-.46f,1.6f,.05f,.06f);
        DisplayEntity.TextDisplayEntity text=new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY,world);tag(text,p);
        text.refreshPositionAndAngles(p.x()+.5,p.y()+3,p.z()+.5,0,0);
        NbtCompound t=new NbtCompound();text.writeNbt(t);
        t.putString("text",Text.Serializer.toJson(Text.literal("§a§l市场交易所§r\n"+p.name()+"\n§f右键 · 挂牌 / 买入 / 撤单")));
        t.putString("billboard","center");t.putString("alignment","center");t.putInt("line_width",220);t.putInt("background",0x99001810);t.putBoolean("shadow",true);t.putByte("text_opacity",(byte)-1);text.readNbt(t);world.spawnEntity(text);
    }
    private static void part(ServerWorld world,MarketOutlet p,Block block,float x,float y,float z,float sx,float sy,float sz) {
        DisplayEntity.BlockDisplayEntity d=new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY,world);tag(d,p);d.refreshPositionAndAngles(p.x()+.5,p.y()+1,p.z()+.5,0,0);
        NbtCompound n=new NbtCompound();d.writeNbt(n);NbtCompound s=new NbtCompound();s.putString("Name",Registries.BLOCK.getId(block).toString());n.put("block_state",s);
        NbtCompound tr=new NbtCompound();tr.put("translation",vec(x,y,z));tr.put("scale",vec(sx,sy,sz));tr.put("left_rotation",vec(0,0,0,1));tr.put("right_rotation",vec(0,0,0,1));n.put("transformation",tr);d.readNbt(n);world.spawnEntity(d);
    }
    private static NbtList vec(float... values) {NbtList list=new NbtList();for(float v:values)list.add(NbtFloat.of(v));return list;}
}
