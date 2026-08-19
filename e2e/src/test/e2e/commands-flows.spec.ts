import { expect, test } from '@drownek/plugwright';
import { Vec3 } from 'vec3';

const named = (fragment: string) => (item: any) => item.getDisplayName().includes(fragment);

async function closeMenu(player: any) {
  const window = player.bot.currentWindow;
  if (window) player.bot.closeWindow(window);
  await expect.poll(() => player.getCurrentGui()).toBeNull();
}

test('enforces player and admin command boundaries on Paper', async ({ player, server }) => {
  player.chat('/growapet doctor');
  await expect(player).toHaveReceivedMessage('ɪɴᴠᴀɪʟᴅ ᴄᴏᴍᴍᴀɴᴅ');

  server.execute('stats');
  await expect(server).toHaveReceivedMessage('Only players can use this command.');

  await player.makeOp();
  player.chat('/growapet doctor extra');
  await expect(player).toHaveReceivedMessage('Invalid command, value, or target.');

  player.chat('/growapet doctor');
  await expect(player).toHaveReceivedMessage('GROWAPET READINESS CHECK');
  await expect(player).toHaveReceivedMessage('READY CHECK PASSED');
});

test('runs plot, currency, egg, pet, quest, and PlaceholderAPI flows', async ({ player, server }) => {
  await player.makeOp();

  // The production plot generator is intentionally a void world.  Give this
  // disposable E2E plot a small valid floor so the real safe-home path can be
  // exercised without changing production world-generation behavior.
  player.chat('/plot settings');
  const plotGui = await player.gui({ title: /PLOT SETTINGS/ });
  const plotLabel = plotGui.locator(named('PLOT #')).displayName();
  const plotId = Number(plotLabel.match(/PLOT #(\d+)/)?.[1]);
  if (!Number.isInteger(plotId)) throw new Error(`Could not read plot id from ${plotLabel}`);
  const gridWidth = 10;
  const spacing = 64;
  const index = plotId - 1;
  const centerX = (index % gridWidth) * spacing;
  const centerZ = Math.floor(index / gridWidth) * spacing;
  await closeMenu(player);

  player.chat('/plot home');
  await expect(player).toHaveReceivedMessage('Teleported to your plot.');
  await expect(player).toBeNear(centerX, 100, centerZ, { tolerance: 1 });
  // /plot home loads the destination chunk. Fill the disposable floor only
  // after that real teleport, then resolve home again through the safe-floor
  // branch rather than relying on a guessed world-load timing.
  server.execute(`minecraft:execute in growapet_plots_e2e run minecraft:setblock ${centerX} 99 ${centerZ} minecraft:stone`);
  player.chat('/plot home');
  await expect(player).toHaveReceivedMessage('Teleported to your plot.');
  await expect(player).toBeNear(centerX, 100, centerZ, { tolerance: 1 });

  player.chat(`/growapet setcoins ${player.username} 777`);
  await expect(player).toHaveReceivedMessage('Updated');
  player.chat('/stats');
  const stats = await player.gui({ title: /PLAYER STATISTICS/ });
  await expect(stats.locator(named('CURRENCIES'))).toHaveLore('777');
  await closeMenu(player);

  player.chat(`/getegg ${player.username} CHICKEN 1`);
  await expect(player).toHaveReceivedMessage('Gave');
  await expect(player).toContainItem('turtle_egg');

  const egg = player.bot.inventory.items().find((item: any) => item.name === 'turtle_egg');
  if (!egg) throw new Error('Expected the custom GrowAPet turtle egg in the inventory');
  await player.bot.equip(egg, 'hand');
  const floorPosition = new Vec3(centerX, 99, centerZ);
  await expect.poll(() => player.bot.blockAt(floorPosition)?.name).toBe('stone');
  const floor = player.bot.blockAt(floorPosition);
  if (!floor) throw new Error('Expected the E2E plot floor under the hatch location');
  await player.bot.placeBlock(floor, new Vec3(0, 1, 0));
  await expect(player).toHaveReceivedMessage('Egg placed.');
  await expect(player).toHaveReceivedMessage('PET HATCHED', { timeout: 10_000 });

  player.chat('/pets');
  const pets = await player.gui({ title: /PET COLLECTION/ });
  const hatchedPet = pets.locator((item: any) => item.getDisplayName().includes('CHICKEN'));
  await expect(hatchedPet).toHaveLore('AVAILABLE');
  await hatchedPet.click();
  await expect(player).toHaveReceivedMessage('Equipped');
  await expect(hatchedPet).toHaveLore('EQUIPPED');
  await closeMenu(player);

  player.chat('/quests claim missing_quest');
  await expect(player).toHaveReceivedMessage('Unknown quest.');

  server.execute(`papi parse ${player.username} %growapet_level%`);
  await expect(server).toHaveReceivedMessage(/INFO\]: 1\s*$/);
});

test('persists option changes and routes a two-player trade request', async ({ player, createPlayer }) => {
  player.chat('/options');
  let options = await player.gui({ title: /PLAYER OPTIONS/ });
  const actionBar = options.locator(named('ACTION BAR'));
  await expect(actionBar).toHaveLore('ENABLED');
  await actionBar.click();
  await expect(player).toHaveReceivedMessage('OPTIONS UPDATED');
  await closeMenu(player);

  player.chat('/options');
  options = await player.gui({ title: /PLAYER OPTIONS/ });
  await expect(options.locator(named('ACTION BAR'))).toHaveLore('DISABLED');
  await options.locator(named('ACTION BAR')).click();
  await expect(player).toHaveReceivedMessage('OPTIONS UPDATED');
  await closeMenu(player);

  const partner = await createPlayer({ username: 'TradePartner' });
  // Trade requests are deliberately disabled until the recipient's persisted
  // options have finished loading. Opening the real options menu provides a
  // synchronization point without introducing a timing sleep.
  partner.chat('/options');
  await partner.gui({ title: /PLAYER OPTIONS/ });
  await closeMenu(partner);
  const normalizeSuggestion = (value: any) => typeof value === 'string' ? value : value.match ?? value.text ?? String(value);
  await expect.poll(async () => {
    const suggestions = await player.bot.tabComplete('/trade ');
    return suggestions.map(normalizeSuggestion).includes('TradePartner');
  }).toBeTruthy();
  player.chat('/trade TradePartner');
  await expect(partner).toHaveReceivedMessage('TRADE REQUEST');
  partner.chat('/trade accept');
  await expect(player).toHaveReceivedMessage('Trade opened.');
  player.chat('/trade offer coins nope');
  await expect(player).toHaveReceivedMessage('Invalid trade command or amount.');
  player.chat('/trade cancel');
  await expect(player).toHaveReceivedMessage('Trade cancelled.');
  await expect(partner).toHaveReceivedMessage('Trade cancelled.');
});

test('filters live tab completion and exercises admin item delivery', async ({ player }) => {
  await player.makeOp();
  const suggestions = await player.bot.tabComplete('/getegg ');
  const suggestionText = suggestions.map((value: any) => typeof value === 'string' ? value : value.match ?? value.text ?? String(value));
  if (suggestionText.some(value => value.includes('%PLAYER%'))) {
    throw new Error(`Tab completion exposed the unresolved %PLAYER% token: ${suggestionText.join(', ')}`);
  }
  if (!suggestionText.includes(player.username)) {
    throw new Error(`Tab completion did not include the online player: ${suggestionText.join(', ')}`);
  }

  player.chat(`/getpet ${player.username} ZOMBIE`);
  await expect(player).toHaveReceivedMessage('custom');
  await expect(player).toContainItem('zombie_spawn_egg');
});
