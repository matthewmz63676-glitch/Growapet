import { expect, test } from '@drownek/plugwright';

test('player reaches the GrowAPet admin command surface', async ({ player }) => {
  await player.makeOp();
  player.chat('/growapet');
  await expect(player).toHaveReceivedMessage('GROWAPET ADMIN');
});

test('player can open the stats and options flows', async ({ player }) => {
  player.chat('/stats');
  const stats = await player.gui({ title: /PLAYER STATISTICS/ });
  await expect(stats.locator(item => item.getDisplayName().includes('CURRENCIES')))
    .toHaveLore('Coins');
  player.bot.closeWindow(player.bot.currentWindow ?? player.bot.inventory);

  player.chat('/options');
  const options = await player.gui({ title: /PLAYER OPTIONS/ });
  await expect(options.locator(item => item.getDisplayName().includes('ACTION BAR')))
    .toHaveLore('Display live GrowAPet progress');
  await options.locator(item => item.getDisplayName().includes('ACTION BAR')).click();
  await expect(player).toHaveReceivedMessage('OPTIONS UPDATED');
  player.bot.closeWindow(player.bot.currentWindow ?? player.bot.inventory);

  player.chat('/daily');
  await player.gui({ title: /DAILY REWARD/ });
  player.bot.closeWindow(player.bot.currentWindow ?? player.bot.inventory);

  player.chat('/quests');
  await player.gui({ title: /QUEST JOURNAL/ });
});

test('admin readiness diagnostics are exposed to a player', async ({ player }) => {
  await player.makeOp();
  player.chat('/growapet doctor');
  await expect(player).toHaveReceivedMessage('GROWAPET READINESS CHECK');
});
