package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.seasons.SeasonService;
import me.growapet.utils.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Secure seasonal reward-track GUI. */
public final class SeasonMenu extends Menu {
    private final GrowAPet plugin;
    private final String seasonId;
    public SeasonMenu(GrowAPet plugin, Player viewer, String seasonId) { super(viewer, Messages.parse("<gold><bold>SEASON JOURNAL</bold></gold>"), 54); this.plugin=plugin; this.seasonId=seasonId; }

    @Override public void build() {
        fill(); SeasonService.SeasonDefinition definition=plugin.getSeasonService().definition(seasonId);
        if(definition==null){setItem(22,item(Material.BARRIER,"<red><bold>SEASON UNAVAILABLE</bold></red>",List.of("<gray>• This season is not configured.</gray>")),null);return;}
        String timezone = plugin.getConfigManager().seasons().getString("timezone", "America/New_York");
        ZoneId zone;
        try { zone = ZoneId.of(timezone); } catch (Exception ignored) { zone = ZoneId.of("America/New_York"); }
        String schedule = plugin.getSeasonService().startsAt(seasonId) > 0
                ? format(plugin.getSeasonService().startsAt(seasonId), zone) + " → " + format(plugin.getSeasonService().endsAt(seasonId), zone)
                : "Not started";
        setItem(4,item(Material.CLOCK,"<gold><bold>"+safe(definition.displayName())+"</bold></gold>",List.of("<gray>• Duration → <white>"+definition.durationDays()+" days</white></gray>","<gray>• Schedule → <white>" + schedule + "</white></gray>",plugin.getSeasonService().isActive(seasonId)?"<green>• Status → active</green>":"<yellow>• Status → inactive</yellow>","<yellow>• Rewards are claim-once and restart-safe.</yellow>")),null);
        plugin.getSeasonService().views(viewer.getUniqueId(),seasonId).whenComplete((views,error)->org.bukkit.Bukkit.getScheduler().runTask(plugin,()->{if(error!=null||!isActive())return;int slot=10;for(SeasonService.ObjectiveView view:views){if(slot>=28)break;long progress=Math.min(view.progress(),view.amount());setItem(slot++,item(progress>=view.amount()?Material.LIME_DYE:Material.WHEAT,"<white><bold>"+safe(view.id())+"</bold></white>",List.of("<gray>• Objective → <white>"+view.type()+"</white></gray>","<gray>• Progress → <white>"+progress+" / "+view.amount()+"</white></gray>",progress>=view.amount()?"<green>Complete</green>":"<yellow>In progress</yellow>")),null);}}));
        int rewardSlot=28;
        for(String reward:definition.rewards().keySet()){
            if(rewardSlot>=44)break;
            int chosen=rewardSlot++;
            setItem(chosen,item(Material.CHEST,"<gold><bold>REWARD</bold></gold>",List.of("<gray>• Track item → <white>"+safe(reward)+"</white></gray>","<gray>• Bundle → <white>" + rewardSummary(definition.rewards().get(reward)) + "</white></gray>","<yellow>Loading claim state…</yellow>")),event->claim(reward));
            plugin.getSeasonService().claimed(viewer.getUniqueId(),seasonId,reward).whenComplete((claimed,error)->org.bukkit.Bukkit.getScheduler().runTask(plugin,()->{if(!isActive()||error!=null)return;setItem(chosen,item(Boolean.TRUE.equals(claimed)?Material.LIME_DYE:Material.CHEST,Boolean.TRUE.equals(claimed)?"<green><bold>CLAIMED</bold></green>":"<gold><bold>REWARD</bold></gold>",List.of("<gray>• Track item → <white>"+safe(reward)+"</white></gray>","<gray>• Bundle → <white>"+rewardSummary(definition.rewards().get(reward))+"</white></gray>",Boolean.TRUE.equals(claimed)?"<green>Already claimed</green>":"<yellow>Click → claim when eligible</yellow>")),Boolean.TRUE.equals(claimed)?null:event->claim(reward));}));
        }
        setItem(45,item(Material.BARRIER,"<red><bold>BACK</bold></red>",List.of("<gray>• Click → close</gray>")),event->viewer.closeInventory());
        setItem(49,item(Material.COMPASS,"<aqua><bold>REFRESH</bold></aqua>",List.of("<gray>• Click → refresh progress</gray>")),event->refresh());
    }
    private void claim(String reward){plugin.getSeasonService().claim(viewer,seasonId,reward).thenAccept(success->{if(success&&isActive())org.bukkit.Bukkit.getScheduler().runTask(plugin,this::refresh);else if(isActive())org.bukkit.Bukkit.getScheduler().runTask(plugin,()->Messages.send(viewer,"<yellow>That reward is not ready or has already been claimed.</yellow>"));});}
    private void fill(){ItemStack pane=item(Material.GRAY_STAINED_GLASS_PANE," ",List.of());for(int i=0;i<54;i++)setItem(i,pane,null);}
    private static ItemStack item(Material material,String name,List<String> lore){return new ItemBuilder(material).name(Messages.parse(name)).loreComponents(lore.stream().map(Messages::parse).toList()).build();}
    private static String safe(String value){return value.replace("<","‹").replace(">","›");}
    private static String format(long millis, ZoneId zone) { return DateTimeFormatter.ofPattern("MMM d, HH:mm z").withZone(zone).format(Instant.ofEpochMilli(millis)); }
    private static String rewardSummary(SeasonService.Reward reward) {
        if (reward == null || reward.bundle() == null) return "configured reward";
        var bundle = reward.bundle();
        if (bundle.coins() > 0) return bundle.coins() + " Coins";
        if (bundle.gems() > 0) return bundle.gems() + " Gems";
        if (bundle.credits() > 0) return bundle.credits() + " Credits";
        if (!bundle.entitlements().isEmpty()) return "Cosmetic entitlement";
        return bundle.boosts().isEmpty() ? "configured reward" : "Bound boost item";
    }
}
