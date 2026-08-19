import { expect, test } from '@drownek/plugwright';

const named = (fragment: string) => (item: any) => item.getDisplayName().includes(fragment);

function rawContainerClick(player: any, slot: number, mouseButton: number, mode: number) {
  const window = player.bot.currentWindow;
  if (!window) throw new Error('Expected a live container window before sending a raw click');
  player.bot._client.write('window_click', {
    windowId: window.id,
    stateId: -1,
    slot,
    mouseButton,
    mode,
    changedSlots: [],
    cursorItem: null
  });
}

async function openMenu(player: any, command: string, title: RegExp) {
  player.chat(command);
  return player.gui({ title });
}

async function closeMenu(player: any) {
  const window = player.bot.currentWindow;
  if (window) player.bot.closeWindow(window);
  await expect.poll(() => player.getCurrentGui()).toBeNull();
}

test('renders the core player GUI surfaces with meaningful content', async ({ player }) => {
  let gui = await openMenu(player, '/stats', /PLAYER STATISTICS/);
  await expect(gui.locator(named('CURRENCIES'))).toHaveLore('Coins');
  await expect(gui.locator(named('REFRESH'))).toHaveLore('update live statistics');
  await gui.locator(named('REFRESH')).click();
  await expect(gui.locator(named('CURRENCIES'))).toHaveLore('Gems');
  await closeMenu(player);

  gui = await openMenu(player, '/quests', /QUEST JOURNAL/);
  await expect(gui.locator(named('RESET SCHEDULE'))).toHaveLore('UTC boundaries');
  await expect(gui.locator(named('REWARDS'))).toHaveLore('saved atomically');
  await gui.locator(named('REFRESH')).click();
  await expect(gui.locator(named('RESET SCHEDULE'))).toHaveLore('Daily quests');
  await closeMenu(player);

  gui = await openMenu(player, '/options', /PLAYER OPTIONS/);
  const actionBar = gui.locator(named('ACTION BAR'));
  await expect(actionBar).toHaveLore('Display live GrowAPet progress');
  await expect(actionBar).toHaveLore('ENABLED');
  await gui.locator(named('REFRESH')).click();
  await expect(gui.locator(named('TRADE REQUESTS'))).toHaveLore('Allow online players');
  await closeMenu(player);

  gui = await openMenu(player, '/pets', /PET COLLECTION/);
  await expect(gui.locator(named('YOUR PETS'))).toHaveLore('Collected');
  await expect(gui.locator(named('REFRESH'))).toHaveLore('update live pet data');
  await closeMenu(player);

  gui = await openMenu(player, '/shop', /GEAR SHOP/);
  await expect(gui.locator(named('YOUR BALANCE'))).toHaveLore('Coins');
  await expect(gui.locator(named('ZONE EGG SHOP'))).toHaveLore('browse eggs');
  await expect(gui.locator(named('MULTIPLIER SHOP'))).toHaveLore('permanent player stats');
  await closeMenu(player);

  gui = await openMenu(player, '/shop eggs', /ZONE EGG SHOP/);
  await expect(gui.locator(named('BACK'))).toHaveLore('return to the shop');
  await expect(gui.locator(named('CHICKEN EGG'))).toHaveLore('Incubation');
  await closeMenu(player);

  gui = await openMenu(player, '/store', /CREDIT STORE/);
  await expect(gui.locator(named('YOUR CREDITS'))).toHaveLore('Credits');
  await closeMenu(player);

  gui = await openMenu(player, '/daily', /DAILY REWARD/);
  await expect(gui.locator((item: any) => item.getDisplayName().includes('REWARD'))).toHaveLore('1 Credit');
  await closeMenu(player);

  gui = await openMenu(player, '/warp', /ZONE NAVIGATOR/);
  await expect(gui.locator(named('WORLD PROGRESSION'))).toHaveLore('Select an unlocked zone');
  await expect(gui.locator(named('CLOSE'))).toHaveLore('close this menu');
  await closeMenu(player);
});

test('renders the secondary player GUI surfaces with meaningful content', async ({ player }) => {
  let gui = await openMenu(player, '/cosmetics', /COSMETICS/);
  await expect(gui.locator(named('YOUR STYLE'))).toHaveLore('Ownership is separate');
  await expect(gui.locator(named('REFRESH'))).toHaveLore('refresh ownership');
  await closeMenu(player);

  gui = await openMenu(player, '/season harvest_festival', /SEASON JOURNAL/);
  await expect(gui.locator((item: any) => item.getDisplayName().toLowerCase().includes('harvest festival'))).toHaveLore('Rewards are claim-once');
  await expect(gui.locator(named('REFRESH'))).toHaveLore('refresh progress');
  await closeMenu(player);

  // Profile loading and plot creation are asynchronous on a real Paper
  // server. Wait for the plugin's own readiness message before opening the
  // plot overview so this assertion covers the populated menu state.
  await expect(player).toHaveReceivedMessage('A plot has been created for you');
  gui = await openMenu(player, '/plot settings', /PLOT SETTINGS/);
  await expect(gui.locator((item: any) => item.getDisplayName().includes('PLOT #'))).toHaveLore('Pet slots');
  await expect(gui.locator(named('TELEPORT HOME'))).toHaveLore('safe plot spawn');
  await closeMenu(player);
});

test('navigates, refreshes, and closes GUI sessions through their registered actions', async ({ player }) => {
  let gui = await openMenu(player, '/shop', /GEAR SHOP/);
  await gui.locator(named('ZONE EGG SHOP')).click();
  gui = await player.gui({ title: /ZONE EGG SHOP/ });
  await gui.locator(named('BACK')).click();
  gui = await player.gui({ title: /GEAR SHOP/ });
  await gui.locator(named('MULTIPLIER SHOP')).click();
  gui = await player.gui({ title: /MULTIPLIER SHOP/ });
  await expect(gui.locator(named('Damage Boost'))).toHaveLore('Level');
  await closeMenu(player);

  gui = await openMenu(player, '/stats', /PLAYER STATISTICS/);
  await gui.locator(named('TOTAL STATS')).click();
  await expect(gui.locator(named('STAT BREAKDOWN'))).toHaveLore('Base weapon damage');
  await player.bot.simpleClick.rightMouse(4);
  await expect(gui.locator(named('STAT INFORMATION'))).toHaveLore('maximum hearts');
  await gui.locator(named('STAT INFORMATION')).click();
  await expect(gui.locator(named('HOW TO USE'))).toHaveLore('Left-click');
  await gui.locator(named('CLOSE')).click();
  await expect.poll(() => player.getCurrentGui()).toBeNull();

  gui = await openMenu(player, '/options', /PLAYER OPTIONS/);
  await gui.locator(named('BACK')).click();
  await expect.poll(() => player.getCurrentGui()).toBeNull();
});

test('denies adversarial inventory modes without dispatching menu actions', async ({ player }) => {
  const gui = await openMenu(player, '/options', /PLAYER OPTIONS/);
  const option = gui.locator(named('ACTION BAR'));
  await expect(option).toHaveLore('ENABLED');

  // Mineflayer exposes the same protocol modes used by a real client.
  await player.bot.clickWindow(20, 0, 1); // shift-click
  await player.bot.clickWindow(20, 0, 2); // hotbar swap
  rawContainerClick(player, 20, 40, 2); // offhand swap (protocol button 40)
  await player.bot.clickWindow(20, 0, 4); // drop one
  await player.bot.clickWindow(20, 1, 4); // drop stack
  rawContainerClick(player, -999, 0, 5); // drag start
  rawContainerClick(player, 20, 1, 5); // drag add
  rawContainerClick(player, -999, 2, 5); // drag end
  rawContainerClick(player, 20, 0, 6); // double-click / collect-to-cursor

  await expect.poll(() => player.getCurrentGui()).toBeTruthy();
  await expect(option).toHaveLore('ENABLED');

  const staleWindowId = player.bot.currentWindow?.id;
  if (staleWindowId === undefined) throw new Error('Expected a window id before stale-click coverage');
  player.bot.closeWindow(player.bot.currentWindow!);
  await expect.poll(() => player.getCurrentGui()).toBeNull();
  player.bot._client.write('window_click', {
    windowId: staleWindowId,
    stateId: -1,
    slot: 20,
    mouseButton: 0,
    mode: 0,
    changedSlots: [],
    cursorItem: null
  });

  await expect.poll(() => player.getCurrentGui()).toBeNull();
  await expect(player).not.toHaveReceivedMessage('OPTIONS UPDATED', { timeout: 500 });
  await closeMenu(player);
});
