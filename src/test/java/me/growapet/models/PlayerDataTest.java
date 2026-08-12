package me.growapet.models;
import org.junit.jupiter.api.Test;import java.util.UUID;import static org.junit.jupiter.api.Assertions.*;
class PlayerDataTest{
 @Test void rejectsNegativeSpendingAndSaturatesRewards(){PlayerData data=new PlayerData(UUID.randomUUID(),"test");data.setCoins(10);assertFalse(data.removeCoins(-1));assertEquals(10,data.getCoins());data.setCoins(Long.MAX_VALUE-1);data.addCoinsRaw(100);assertEquals(Long.MAX_VALUE,data.getCoins());}
 @Test void normalizesStoredExperienceAcrossLevels(){PlayerData data=new PlayerData(UUID.randomUUID(),"test");data.setLevel(1);data.setExp(PlayerData.expToLevelUp(1)+5);data.normalizeExperience();assertEquals(2,data.getLevel());assertEquals(5,data.getExp());}
}
