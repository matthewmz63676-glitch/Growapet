package me.growapet.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;import me.growapet.GrowAPet;import me.growapet.leaderboards.MoneySpentManager;import org.bukkit.OfflinePlayer;import org.jetbrains.annotations.NotNull;
public final class TopSpentExpansion extends PlaceholderExpansion{
 private final GrowAPet plugin;public TopSpentExpansion(GrowAPet plugin){this.plugin=plugin;}@Override public @NotNull String getIdentifier(){return"topspent";}@Override public @NotNull String getAuthor(){return"GrowAPet";}@Override public @NotNull String getVersion(){return plugin.getDescription().getVersion();}@Override public boolean persist(){return true;}
 @Override public String onRequest(OfflinePlayer player,@NotNull String params){String[]parts=params.split("_");if(parts.length!=2)return null;int rank;try{rank=Integer.parseInt(parts[1]);}catch(NumberFormatException error){return null;}MoneySpentManager.Entry entry=plugin.getMoneySpentManager().cached(rank);if(parts[0].equalsIgnoreCase("name"))return entry==null?"—":entry.name();if(parts[0].equalsIgnoreCase("amount"))return entry==null?"$0.00":MoneySpentManager.format(entry.cents());return null;}
}
