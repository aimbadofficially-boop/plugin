# Steal an Egg (Paper + Geyser/Floodgate)

A Minecraft minigame plugin inspired by the Bionic "Minecraft STEAL AN EGG" video.
The plugin builds its own map in a void world called `steal_an_egg` the first time it starts.

## Build
Needs JDK 21 and Maven:

    mvn package

The jar ends up in `target/StealAnEgg.jar`. Drop it in `plugins/`.

## Server requirements
- Paper (or a fork) 1.21.x, Java 21
- For Bedrock players: Geyser-Spigot + Floodgate (optional, nothing else to configure)

## How it plays
- Players are teleported into the world on join and get a base (12 bases, coloured).
- **Egg conveyor** in the plaza: tap/right-click an egg to buy it. Cost depends on rarity.
- Eggs sit on pedestals in your base and pay income per second. Each new egg type you
  discover (Egg Index) adds +5% income.
- **Steal**: tap an egg in someone else's base to grab it, then run it back to your own base.
  The carrier glows. A **Knock Stick** (PVP shop) hit knocks the egg loose and returns it to its owner.
- Plaza is a safe zone (no knocking/freezing).
- **PVP shop**: Knock Stick, Freeze Snowballs, Egg Shield.
- **Trail shop**: particle trails that also give a permanent speed bonus.
- **Egg Index**: shows the eggs you have discovered.
- **Speed Gym**: 4 treadmills (Jogger, Runner, Sprinter, Lightning). Run forward on the belt to
  earn speed points; more points = higher walk speed. Faster treadmills need more points first.

## Commands
- `/base`, `/spawn`
- `/sae rebuildmap`, `/sae give <player> <cash>`, `/sae speed <player> <points>` (permission `stealanegg.admin`)

## Config
`plugins/StealAnEgg/config.yml`: start cash, shop prices, conveyor speed, treadmill gains /
requirements / belt push strength, speed per point, max speed multiplier.

## Notes
- Chest GUIs, signs, action bars and the sidebar all work through Geyser.
- Nothing here was tested on a live server yet - see the tuning tips below.
- Tuning: if players can stand still on a treadmill raise `treadmills.push`; if egg
  armor stands look too high/low change the `F + 1.2` / `F + 0.75` offsets in `Game.java`.
