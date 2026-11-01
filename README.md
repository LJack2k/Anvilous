<img src="branding/icon-256.png" alt="" width="128" align="right">

# Anvilous

Repair your gear at the **smithing table**, with materials and amounts a datapack decides —
and leave the anvil's experience bill behind.

**Minecraft 1.21.1 · NeoForge · client and server**

> **Status: in design.** Nothing is built yet. This README is the agreed spec; the format
> below is what the mod is being written against.

---

## What it does

Put a damaged tool in the smithing table's base slot and a material in the addition slot.
A recipe says which materials that tool accepts and how much durability each one gives, as a
percentage of the item's maximum. The repaired tool comes out the other side with every
enchantment and component untouched, and it costs no experience — only the material.

The anvil can already do some of this, badly. It charges levels, it raises the price every
time you use it, and eventually it refuses outright. Anvilous moves the job to a block that
doesn't care.

> **On its own it does nothing.** No recipes ship enabled by default beyond the starter pack
> below — a pack decides which tools can be repaired, by what, and for how much.

Both sides need the mod: recipes are synced to every client, and the smithing table filters
its input slots against the client's own recipe list.

## Repair recipes

One file per tool or tool group, in `data/<namespace>/recipe/`:

```json
{
  "type": "anvilous:smithing_repair",
  "base": { "tag": "c:tools/pickaxes" },
  "materials": [
    { "tag":  "c:ingots/iron",             "percent": 25 },
    { "item": "minecraft:diamond",         "percent": 50 },
    { "item": "minecraft:netherite_ingot", "percent": 100 }
  ],
  "keep_enchantments": true,
  "repair_cost": "reset"
}
```

### Fields

| Field | Meaning |
| --- | --- |
| `base` | The item being repaired. A `tag` or an `item`. |
| `materials` | A list; each entry is a `tag` **or** an `item`, with its own `percent`. Tags and items can be mixed freely in one recipe. |
| `percent` | Durability restored **per material item**, as a percentage of the tool's maximum. A 1562-durability diamond pickaxe at 100/1562, repaired with a 50% material, comes out at 881/1562. Flat, predictable, independent of how damaged the tool is. |
| `max_per_craft` | *Optional.* The most material items one craft may take. Leave it out and a craft does as much as the materials allow. Set it only to make a full repair take several crafts. |
| `keep_enchantments` | Keeps enchantments and components on the repaired item. Normally `true` — it is the reason this mod exists. |
| `repair_cost` | What to do with the item's hidden anvil-use counter: `reset`, `keep` or `increase`. See below. |
| `template` | *Optional.* An item required in the template slot, if a pack wants the repair gated behind something. Omit it and the slot stays empty. |

### What a repair costs

**Takes what it needs, up to what you gave it.** Drop a stack of diamonds in beside a nearly
broken pickaxe and it comes out full, having spent four; do the same beside a pickaxe one
point from full and it spends one. There is no waste and no setting to tune, because the
slot is already the player's own limit — they decide what a craft may spend by deciding what
to put in it.

Vanilla's smithing menu removes exactly one item per craft, so taking the right number needs
a mixin on `SmithingMenu`; it is in `nl.ljack2k.anvilous.mixin`.

## Enchanting recipes

The same table applies an enchantment from a book, paid for in materials rather than
experience:

```json
{
  "type": "anvilous:smithing_enchant",
  "enchantment": "minecraft:mending",
  "base": { "item": "minecraft:diamond_pickaxe" },
  "materials": [
    { "item": "minecraft:diamond", "per_level": 4 }
  ]
}
```

Book in the template slot, tool in the base slot, payment in the addition slot. The book is
consumed, as it is in an anvil.

The recipe names the **enchantment**, not the book. That is what makes per-enchantment
pricing possible: an ingredient matches items and tags but ignores components, so it could
say "an enchanted book" but never "a Mending book". The book is only where the level is read
from, which is also why `per_level` works — a Sharpness recipe at `"per_level": 2` charges 2
ingots for Sharpness I and 10 for Sharpness V.

| Field | Meaning |
| --- | --- |
| `enchantment` | The enchantment this recipe prices, by id. |
| `base` | What it may be applied to. A `tag` or an `item`. |
| `materials` | Each entry is a `tag` **or** an `item`, with its own `per_level`. |
| `per_level` | Items charged **per level** of the enchantment on the book. |
| `repair_cost` | As for repair: `reset`, `keep` or `increase`. |

Combining follows vanilla's anvil rules, reusing vanilla's own helpers: two equal levels make
the next one up, levels cap at the enchantment's maximum, incompatible enchantments refuse,
and whether an item accepts an enchantment is asked of the item, so modded tools answer for
themselves. A craft that would change nothing is refused rather than charged for.

### `repair_cost`

Every item carries a hidden counter of how many times it has been through an anvil. The anvil
charges it, doubles it (`0 → 1 → 3 → 7 → 15 → 31`), and eventually says *Too Expensive!*.
The smithing table never reads it, so repairs here stay cheap no matter what it says.

- `reset` *(default)* — set it back to zero. The tool comes out clean for any later anvil use.
- `keep` — leave it alone.
- `increase` — grow it the way the anvil would, for packs that want repairs to get costlier.

## Starter recipes

A built-in pack translates vanilla's own repair rules: `percent: 25`, `max_per_craft: 4`, the
repair material for each tier. That is exactly what an anvil gives you, minus the experience.
Packs are expected to replace or extend it.

## Building

`gradlew build` produces the jar under `neoforge/build/libs/`.

To have each build land in a launcher instance as well, create a `local.properties`
next to `settings.gradle` (it is gitignored) with the instance's game directory:

```properties
prism_instance=C:/Users/you/AppData/Roaming/PrismLauncher/instances/Anvilous Dev/minecraft
```

`build` then runs `installToPrism`, which clears out older `Anvilous-*.jar` files and copies
the new one in. Without the property the task is never registered, so the build is unchanged.

## License

MIT
