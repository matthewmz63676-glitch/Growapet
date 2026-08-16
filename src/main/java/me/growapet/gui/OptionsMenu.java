package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class OptionsMenu extends Menu {
    private final GrowAPet plugin;
    private boolean saving;

    public OptionsMenu(GrowAPet plugin, Player viewer) {
        super(viewer, Messages.parse("<aqua><bold>PLAYER OPTIONS</bold></aqua>"), 45);
        this.plugin = plugin;
    }

    @Override public void build() {
        fill();
        boolean actionbar=plugin.getOptionsManager().enabled(viewer.getUniqueId(),"actionbar",true);
        boolean trades=plugin.getOptionsManager().enabled(viewer.getUniqueId(),"trade_requests",true);
        boolean relay=plugin.getOptionsManager().enabled(viewer.getUniqueId(),"discord_relay",true);

        setItem(4,item(Material.PLAYER_HEAD,"<aqua><bold>YOUR PREFERENCES</bold></aqua>",List.of(
                "<gray>Choose how GrowAPet interacts with you.</gray>",
                "",
                "<gray>• Changes → <white>apply immediately</white></gray>",
                "<gray>• Storage → <white>saved per player</white></gray>")),null);

        setItem(20,option(Material.NETHER_STAR,"ACTION BAR",actionbar,List.of(
                "<gray>Display live GrowAPet progress</gray>",
                "<gray>above your hotbar.</gray>")),saving?null:event->toggle("actionbar",actionbar));

        setItem(22,option(Material.EMERALD,"TRADE REQUESTS",trades,List.of(
                "<gray>Allow online players to send</gray>",
                "<gray>you new trade requests.</gray>")),saving?null:event->toggle("trade_requests",trades));

        setItem(24,option(Material.PAPER,"DISCORD RELAY",relay,List.of(
                "<gray>Allow your chat to relay</gray>",
                "<gray>to the configured Discord channel.</gray>")),saving?null:event->toggle("discord_relay",relay));

        if(saving)setItem(31,item(Material.CLOCK,"<yellow><bold>SAVING CHANGES…</bold></yellow>",List.of("<gray>• Please wait a moment</gray>")),null);
        else setItem(31,item(Material.PAPER,"<aqua><bold>HOW TO USE</bold></aqua>",List.of(
                "<gray>• Click an option → toggle it</gray>",
                "<gray>• Green → enabled</gray>",
                "<gray>• Red → disabled</gray>")),null);

        setItem(36,item(Material.BARRIER,"<red><bold>BACK</bold></red>",List.of("<gray>• Click → close this menu</gray>")),event->viewer.closeInventory());
        setItem(40,item(Material.COMPASS,"<aqua><bold>REFRESH</bold></aqua>",List.of("<gray>• Click → refresh saved preferences</gray>")),saving?null:event->refresh());
    }

    private void toggle(String key,boolean current){
        if(saving)return;
        saving=true;
        boolean next=!current;
        if(key.equals("trade_requests"))plugin.getTradeManager().onTradePreferenceChanged(viewer.getUniqueId(),next);
        refresh();
        plugin.getOptionsManager().set(viewer.getUniqueId(),key,String.valueOf(next)).whenComplete((ignored,error)->
                Bukkit.getScheduler().runTask(plugin,()->{
                    saving=false;
                    if(error!=null){
                        if(key.equals("trade_requests"))plugin.getTradeManager().onTradePreferenceChanged(viewer.getUniqueId(),current);
                        if(viewer.isOnline())Messages.send(viewer,"<red>Could not save that preference. Your previous setting was restored.</red>");
                    }else if(viewer.isOnline()){
                        String name=key.equals("actionbar")?"Action bar":key.equals("trade_requests")?"Trade requests":"Discord relay";
                        Messages.send(viewer,"<aqua><bold>OPTIONS UPDATED</bold></aqua> <dark_gray>•</dark_gray> <gray><name> → <state></gray>",Messages.value("name",name),Messages.value("state",next?"enabled":"disabled"));
                    }
                    if(isActive()&&viewer.getOpenInventory().getTopInventory()==getInventory())refresh();
                }));
    }

    private ItemStack option(Material material,String title,boolean enabled,List<String> description){
        List<String> lore=new java.util.ArrayList<>(description);
        lore.add("");
        lore.add("<gray>• Status → "+(enabled?"<green><bold>ENABLED</bold></green>":"<red><bold>DISABLED</bold></red>")+"</gray>");
        lore.add("<gray>• Click → "+(enabled?"<red>disable</red>":"<green>enable</green>")+"</gray>");
        return new ItemBuilder(material).name(Messages.parse((enabled?"<green>":"<red>")+"<bold>"+title+"</bold>"+(enabled?"</green>":"</red>"))).loreComponents(lore.stream().map(Messages::parse).toList()).glow(enabled).build();
    }

    private void fill(){ItemStack pane=item(Material.GRAY_STAINED_GLASS_PANE," ",List.of());for(int slot=0;slot<45;slot++)setItem(slot,pane,null);ItemStack accent=item(Material.PURPLE_STAINED_GLASS_PANE," ",List.of());for(int slot:new int[]{0,1,7,8,9,17,27,35,37,38,42,43,44})setItem(slot,accent,null);}
    private static ItemStack item(Material material,String name,List<String> lore){return new ItemBuilder(material).name(Messages.parse(name)).loreComponents(lore.stream().map(Messages::parse).toList()).build();}
}
