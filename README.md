# Steal An Egg (Paper plugin)

A recreation of the Roblox game *Steal An Egg* using only vanilla Minecraft mobs.
Requires **Paper 1.21.4+** and **Java 21**.

## Build with GitHub Actions
1. Create a repo and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab -> **Build plugin** -> run it (it also runs on every push).
3. Download `StealAnEgg-jar` from the run, unzip, drop `StealAnEgg.jar` into `plugins/`.

## First start
The plugin creates a void world `sae_world`, builds the T-shaped map and teleports players to their base.
Config: `plugins/StealAnEgg/config.yml`. Data: `plugins/StealAnEgg/data.db` (SQLite).

## Gameplay
Go down the long path, right-click a guarded egg to steal it, outrun the guardian mobs, walk into your base to
incubate it, collect pet income at the gold block, train on the treadmill, buy trails, upgrade your base.
Right-click a player carrying an egg (outside the safe zone) or a growing egg in another base to steal it.

## Commands
Players: `/sae home|spawn|stats|collect|help`
Admins (OP / `stealanegg.admin`): `/sae give|set|trail|reset|info|plot|tp|bypass|resetnests|eggs|rebuild|reload`
