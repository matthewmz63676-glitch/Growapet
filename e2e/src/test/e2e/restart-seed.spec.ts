import { expect, test } from '@drownek/plugwright';
import { Vec3 } from 'vec3';

const named = (fragment: string) => (item: any) => item.getDisplayName().includes(fragment);

function plotCenter(plotId: number): { x: number; z: number } {
  const index = plotId - 1;
  return { x: (index % 10) * 64, z: Math.floor(index / 10) * 64 };
}

async function closeMenu(player: any) {
  const window = player.bot.currentWindow;
  if (window) player.bot.closeWindow(window);
  await expect.poll(() => player.getCurrentGui()).toBeNull();
}

async function waitForBlock(player: any, position: Vec3, blockName: string) {
  await expect.poll(() => player.bot.blockAt(position)?.name).toBe(blockName);
}

async function waitForEgg(player: any) {
  await expect.poll(() => player.bot.inventory.items().some((item: any) => item.name === 'turtle_egg')).toBeTruthy();
  const egg = player.bot.inventory.items().find((item: any) => item.name === 'turtle_egg');
  if (!egg) throw new Error('The seed player inventory changed before the egg could be equipped');
  return egg;
}

test('seeds durable options, balances, a pet, and an active incubation before restart', async ({ createPlayer, server }) => {
  const player = await createPlayer({ username: 'RestartPlayer' });
  await player.makeOp();

  player.chat('/plot settings');
  const plotGui = await player.gui({ title: /PLOT SETTINGS/ });
  const plotLabel = plotGui.locator(named('PLOT #')).displayName();
  const plotId = Number(plotLabel.match(/PLOT #(\d+)/)?.[1]);
  if (!Number.isInteger(plotId)) throw new Error(`Could not read plot id from ${plotLabel}`);
  await closeMenu(player);

  player.chat('/options');
  let options = await player.gui({ title: /PLAYER OPTIONS/ });
  await expect(options.locator(named('ACTION BAR'))).toHaveLore('ENABLED');
  await options.locator(named('ACTION BAR')).click();
  // The refreshed item is the durable success signal; the smoke suite separately
  // asserts the accompanying player message.
  await expect(options.locator(named('ACTION BAR'))).toHaveLore('DISABLED');
  await closeMenu(player);

  player.chat(`/growapet setcoins ${player.username} 4242`);
  await expect(player).toHaveReceivedMessage('Updated');

  player.chat('/plot home');
  await expect(player).toHaveReceivedMessage('Teleported to your plot.');
  const center = plotCenter(plotId);
  const floorPosition = new Vec3(center.x, 99, center.z);
  const eggPosition = new Vec3(center.x, 100, center.z);
  server.execute(`minecraft:execute in growapet_plots_e2e run minecraft:setblock ${center.x} 99 ${center.z} minecraft:stone`);
  await waitForBlock(player, floorPosition, 'stone');

  // Hatch one short-lived egg so the pet row and equipped state are durable.
  player.chat(`/getegg ${player.username} CHICKEN 1`);
  await expect(player).toHaveReceivedMessage('Gave');
  const firstEgg = await waitForEgg(player);
  await player.bot.equip(firstEgg, 'hand');
  const floor = player.bot.blockAt(floorPosition);
  if (!floor) throw new Error('The E2E floor block was not visible to Mineflayer');
  await player.bot.placeBlock(floor, new Vec3(0, 1, 0));
  await expect(player).toHaveReceivedMessage('Egg placed.');
  await expect(player).toHaveReceivedMessage('PET HATCHED', { timeout: 10_000 });

  player.chat('/pets');
  const pets = await player.gui({ title: /PET COLLECTION/ });
  const hatchedPet = pets.locator(named('CHICKEN'));
  await expect(hatchedPet).toHaveLore('AVAILABLE');
  await hatchedPet.click();
  await expect(player).toHaveReceivedMessage('Equipped');
  await closeMenu(player);

  // The second egg remains active across the two sequential Paper processes.
  player.chat(`/getegg ${player.username} CHICKEN 90`);
  await expect(player).toHaveReceivedMessage('Gave');
  const secondEgg = await waitForEgg(player);
  await player.bot.equip(secondEgg, 'hand');
  const refreshedFloor = player.bot.blockAt(floorPosition);
  if (!refreshedFloor) throw new Error('The E2E floor block disappeared before the second placement');
  await player.bot.placeBlock(refreshedFloor, new Vec3(0, 1, 0));
  await expect(player).toHaveReceivedMessage('Egg placed.');
  await waitForBlock(player, eggPosition, 'turtle_egg');

  options = await (async () => {
    player.chat('/options');
    return player.gui({ title: /PLAYER OPTIONS/ });
  })();
  await expect(options.locator(named('ACTION BAR'))).toHaveLore('DISABLED');
  await closeMenu(player);
});
