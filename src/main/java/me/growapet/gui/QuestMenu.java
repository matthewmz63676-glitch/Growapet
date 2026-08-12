package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.quests.QuestDefinition;
import me.growapet.quests.QuestManager;
import me.growapet.utils.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuestMenu extends Menu {
    private static final NumberFormat NUMBER=NumberFormat.getIntegerInstance(Locale.US);
    private static final int[] QUEST_SLOTS={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    private final GrowAPet plugin;

    public QuestMenu(GrowAPet plugin,Player viewer){super(viewer,Messages.parse("<light_purple><bold>QUEST JOURNAL</bold></light_purple>"),54);this.plugin=plugin;}

    @Override public void build(){
        fill();
        List<QuestManager.QuestView> views=plugin.getQuestManager().views(viewer.getUniqueId());
        for(int index=0;index<Math.min(views.size(),QUEST_SLOTS.length);index++){
            QuestManager.QuestView view=views.get(index);boolean ready=!view.claimed()&&view.progress()>=view.definition().amount();
            setItem(QUEST_SLOTS[index],questItem(view),ready?event->claim(view.definition().key()):null);
        }
        setItem(4,item(Material.CLOCK,"<aqua><bold>RESET SCHEDULE</bold></aqua>",List.of(
                "<gray>• Daily quests → <white>"+plugin.getQuestManager().timeUntilReset("daily")+"</white></gray>",
                "<gray>• Weekly quests → <white>"+plugin.getQuestManager().timeUntilReset("weekly")+"</white></gray>",
                "<dark_gray>Progress resets at UTC boundaries.</dark_gray>")),null);
        setItem(45,item(Material.BARRIER,"<red><bold>BACK</bold></red>",List.of("<gray>• Click → close the quest journal</gray>")),event->viewer.closeInventory());
        setItem(49,item(Material.COMPASS,"<aqua><bold>REFRESH</bold></aqua>",List.of("<gray>• Click → update quest progress</gray>")),event->refresh());
        setItem(53,item(Material.CHEST,"<light_purple><bold>REWARDS</bold></light_purple>",List.of("<gray>• Complete objectives</gray>","<gray>• Click a ready quest → claim</gray>","<gray>• Rewards are saved atomically</gray>")),null);
    }

    private void claim(String key){plugin.getQuestManager().claim(viewer,key).thenAccept(success->{if(success&&isActive()&&viewer.getOpenInventory().getTopInventory()==getInventory())refresh();});}

    private ItemStack questItem(QuestManager.QuestView view){
        QuestDefinition definition=view.definition();long progress=Math.min(view.progress(),definition.amount());boolean ready=!view.claimed()&&progress>=definition.amount();
        Material icon=view.claimed()?Material.LIME_DYE:ready?Material.CHEST_MINECART:switch(definition.group()){case"daily"->Material.SUNFLOWER;case"weekly"->Material.CLOCK;default->Material.BOOK;};
        String status=view.claimed()?"<green>CLAIMED</green>":ready?"<aqua>READY TO CLAIM</aqua>":"<yellow>IN PROGRESS</yellow>";
        List<String> lore=new ArrayList<>();
        lore.add("<gray>• Category → <white>"+title(definition.group())+"</white></gray>");
        lore.add("<gray>• Progress → <white>"+NUMBER.format(progress)+" / "+NUMBER.format(definition.amount())+"</white></gray>");
        lore.add("<gray>• Status → "+status+"</gray>");
        lore.add("");lore.add("<gold><bold>REWARDS</bold></gold>");
        if(definition.coins()>0)lore.add("<gray>• Coins → <yellow>"+NUMBER.format(definition.coins())+"</yellow></gray>");
        if(definition.gems()>0)lore.add("<gray>• Gems → <aqua>"+NUMBER.format(definition.gems())+"</aqua></gray>");
        if(definition.credits()>0)lore.add("<gray>• Credits → <light_purple>"+NUMBER.format(definition.credits())+"</light_purple></gray>");
        if(definition.exp()>0)lore.add("<gray>• Experience → <green>"+NUMBER.format(definition.exp())+"</green></gray>");
        if(ready){lore.add("");lore.add("<aqua><bold>CLICK → CLAIM REWARD</bold></aqua>");}
        else if(!view.claimed()){lore.add("");lore.add("<dark_gray>Resets in "+plugin.getQuestManager().timeUntilReset(definition.group())+".</dark_gray>");}
        return item(icon,"<white><bold>"+escape(definition.name())+"</bold></white>",lore);
    }

    private void fill(){ItemStack pane=item(Material.GRAY_STAINED_GLASS_PANE," ",List.of());for(int slot=0;slot<54;slot++)setItem(slot,pane,null);}
    private static ItemStack item(Material material,String name,List<String> lore){return new ItemBuilder(material).name(Messages.parse(name)).loreComponents(lore.stream().map(Messages::parse).toList()).build();}
    private static String title(String value){return value.substring(0,1).toUpperCase(Locale.ROOT)+value.substring(1).toLowerCase(Locale.ROOT);}
    private static String escape(String value){return value.replace("<","‹").replace(">","›");}
}
