# GeyserAC

Anti-cheat for Bedrock players who join a Java server through [Geyser](https://geysermc.org). Java anti-cheats like GrimAC skip Geyser players entirely, so without something on the Geyser side a Bedrock client can fly, speed and reach freely.

GeyserAC is a Geyser extension. It runs its own copy of Bedrock movement physics for every Bedrock player, checked tick for tick against a real Bedrock Dedicated Server, and sets the player back when the client moves somewhere the physics can't reach.

## Checks

- **Movement**: walking, sprinting, sneaking, jumping, swimming, ladders and vines, ice, slime, honey, soul sand, cobwebs, powder snow, scaffolding, bubble columns, beds, elytra and rockets, riptide, effects and knockback. Covers fly, speed, step, jesus, spider, anti-knockback and similar.
- **Vehicles**: horses, donkeys, mules, camels and boats are predicted too; pigs, striders and minecarts are already moved by the server.
- **NoFall**, **Timer** (the client may not send inputs faster than real time), **NoSlow** (item use slowdown), sprint rules (hunger, blindness), bad input (NaN, pitch over 90).
- **Combat**: reach (3.3 blocks to the drawn hitbox, with lag compensation), hitboxes (the look must actually meet the target), attacks through walls, attacking yourself, attacking while using an item.
- **Blocks**: placement and break reach and aim, through-wall clicks, FastBreak, nuker (many blocks at once), fast place, invalid block faces. Bedrock's Haste is stronger than Java's, so Haste levels shown to Bedrock clients are lowered to match Java break times.
- **Inventory**: moving or attacking with an inventory screen open.

Keyboard, gamepad and touch input are all handled. Lag compensation uses a ping sent with every batch of changes, so a state change (speed, effects, knockback, a block update) only counts once the client has seen it.

## Install

1. Server with Geyser 2.12 or newer (tested on Geyser-Fabric; Floodgate optional).
2. Put `PandaGeyserAC.jar` in Geyser's `extensions/` folder (`config/Geyser-Fabric/extensions/` on Fabric) and restart.
3. Create an empty file named `enforce` in `extensions/pandageyserac/` to turn setbacks on. Without it the extension only logs. The file is checked while running, so you can switch without a restart.

Optional: an empty `verbose` file there writes every tick to `moves.log` (large). `-Dpandageyserac.reach=3.3` changes the attack reach limit.

Flags and setbacks are logged as `[pandageyserac]` in the server log.

## Building

`./gradlew build` (Java 21+). The jar is `build/libs/PandaGeyserAC.jar`.

## License

MIT, see `LICENSE`. Parts are adapted from [Boar](https://github.com/opencollab-incubator/Boar) and [Geyser](https://github.com/GeyserMC/Geyser) (both MIT) and use collision data from [minecraft-data](https://github.com/PrismarineJS/minecraft-data) (MIT); see `THIRD_PARTY_LICENSES.md`.
