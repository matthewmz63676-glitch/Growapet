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

test('restores durable options, balances, equipped pets, and active incubation after restart', async ({ createPlayer }) => {
  const player = await createPlayer({ username: 'RestartPlayer' });

  player.chat('/options');
  const options = await player.gui({ title: /PLAYER OPTIONS/ });
  await expect(options.locator(named('ACTION BAR'))).toHaveLore('DISABLED');
  await closeMenu(player);

  player.chat('/stats');
  const stats = await player.gui({ title: /PLAYER STATISTICS/ });
  await expect(stats.locator(named('CURRENCIES'))).toHaveLore('4,242');
  await closeMenu(player);

  player.chat('/pets');
  const pets = await player.gui({ title: /PET COLLECTION/ });
  await expect(pets.locator(named('YOUR PETS'))).toHaveLore('Collected → 1');
  await expect(pets.locator(named('CHICKEN'))).toHaveLore('EQUIPPED');
  await closeMenu(player);

  player.chat('/plot settings');
  const plotGui = await player.gui({ title: /PLOT SETTINGS/ });
  const plotLabel = plotGui.locator(named('PLOT #')).displayName();
  const plotId = Number(plotLabel.match(/PLOT #(\d+)/)?.[1]);
  if (!Number.isInteger(plotId)) throw new Error(`Could not read plot id from ${plotLabel}`);
  await expect(plotGui.locator(named('PLOT #'))).toHaveLore('Incubators → 1 / 3');
  await closeMenu(player);

  player.chat('/plot home');
  await expect(player).toHaveReceivedMessage('Teleported to your plot.');
  const center = plotCenter(plotId);
  await expect.poll(() => player.bot.blockAt(new Vec3(center.x, 100, center.z))?.name).toBe('turtle_egg');
});
