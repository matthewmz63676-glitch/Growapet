package me.growapet.quests;
public record QuestDefinition(String key,String group,String id,String name,QuestType type,long amount,String target,long coins,long gems,long credits,long exp){}
