<img src="branding/icon-256.png" alt="" width="128" align="right">

# Anvilous

Repair your gear at the **smithing table**, with materials and amounts a datapack decides —
and leave the anvil's experience bill behind.

**Minecraft 1.21.1 · NeoForge · client and server**

---

## What it does

Put a damaged tool in the smithing table's base slot and a material in the addition slot.
A recipe says which materials that tool accepts and how much durability each one gives, as a
percentage of the item's maximum. The repaired tool comes out the other side with every
enchantment and component untouched, and it costs no experience — only the material.

The anvil can already do some of this, badly. It charges levels, it raises the price every
time you use it, and eventually it refuses outright. Anvilous moves the job to a block that
doesn't care.

> **It works out of the box.** Every damageable item in vanilla is covered by the built-in
> recipes below. A pack overrides any of them by shipping a file at the same path.

Both sides need the mod: recipes are synced to every client, and the smithing table filters
its input slots against the client's own recipe list.

## As an anvil replacement

| Anvil operation | Anvilous |
| --- | --- |
| Repair with material units | `anvilous:smithing_repair` |
| Combine two of the same item | `anvilous:smithing_combine` |
| Apply an enchanted book | `anvilous:smithing_enchant` |
| Rename | **not possible** |

Renaming needs a text field, and the smithing table has none. Adding one would mean shipping
a block and a menu of our own, which is exactly what this mod avoids — everything here runs
in the vanilla smithing table, so it cannot collide with whatever else a pack installs. A
pack replacing the anvil entirely still needs something else for naming.

## Repair recipes

One file per tool or tool group, in `data/<namespace>/recipe/`:

```json
{
  "type": "anvilous:smithing_repair",
  "base": { "tag": "minecraft:pickaxes" },
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
| `percent` | Durability restored **per material item**, as a percentage of the tool's maximum. A diamond pickaxe (1561) at 100 damage, repaired with a 25% material, gets 391 back. Flat, predictable, independent of how damaged the tool is. |
| `max_per_craft` | *Optional.* The most material items one craft may take. Leave it out and a craft does as much as the materials allow. Set it only to make a full repair take several crafts. |
| `keep_enchantments` | Keeps enchantments and components on the repaired item. Normally `true` — it is the reason this mod exists. |
| `repair_cost` | What to do with the item's hidden anvil-use counter: `reset`, `keep` or `increase`. See below. |
| `template` | *Optional.* An item required in the template slot, if a pack wants the repair gated behind something. Omit it and the slot stays empty. |

### On tags

Prefer a tag to an item wherever one exists. Modded materials are unified by tag, so
`c:ingots/brass` covers every mod's brass ingot at once and keeps working when a pack swaps
which mod provides it — a recipe keeps its tag rather than resolving it to items, so it picks
up whatever is installed at runtime.

Watch the namespace, though. The `c:` convention covers materials (`c:ingots/iron`,
`c:gems/diamond`) and a handful of tools (`c:tools/bow`, `c:tools/shield`), but the common
tool classes are **vanilla** tags in 1.21.1: `minecraft:pickaxes`, `minecraft:axes`,
`minecraft:shovels`, `minecraft:hoes`, `minecraft:swords`. There is no `c:tools/pickaxes`,
and a recipe naming a tag that does not exist simply never matches — no error, no warning.

### What a repair costs

**Takes what it needs, up to what you gave it.** Drop a stack of diamonds in beside a nearly
broken pickaxe and it comes out full, having spent four; do the same beside a pickaxe one
point from full and it spends one. There is no waste and no setting to tune, because the
slot is already the player's own limit — they decide what a craft may spend by deciding what
to put in it.

Vanilla's smithing menu removes exactly one item per craft, so taking the right number needs
a mixin on `SmithingMenu`; it is in `nl.ljack2k.anvilous.mixin`.

## Combine recipes

Two of the same item, their durability added and their enchantments merged — the anvil's
third trick, and the only repair some items have:

```json
{
  "type": "anvilous:smithing_combine",
  "base": { "item": "minecraft:bow" },
  "repair_cost": "reset"
}
```

Tool in the base slot, the sacrifice in the addition slot. Nine vanilla items have no
material repair at all — bows, crossbows, fishing rods, shears, tridents, flint and steel,
brushes and the two sticks-on-a-string — so without this they cannot be repaired here at all.

The durability sum is vanilla's, bonus included: both items' remaining durability plus 12% of
the maximum. `bonus_percent` changes that for packs that want to; leave it out for parity.

Enchantments on the sacrifice are merged in by the same rules used for books, and ones that
do not fit are skipped rather than failing the craft — again as the anvil does.

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

## Built-in recipes

Anvilous ships 110 recipes, covering every damageable item and every enchantment in vanilla.
None of them are a new balance. Every number in them was read out of the game rather than
decided by us, so what they do is move vanilla's own prices to a block that does not charge
experience and does not get more expensive the more you use it.

A pack replaces any of them by shipping a file at the same path: `data/anvilous/recipe/diamond_pickaxe.json`
in a datapack wins over the mod's copy. There is no way to delete a recipe from JSON, but one
whose `base` matches nothing is inert, so overriding with a recipe for an item that does not
exist is how a pack turns one off.

Both sets come out of generators in the dev harness — `/anvilous dump_repair <percent>` and
`/anvilous dump_enchant` — so a pack with its own tiers regenerates them rather than editing
a hundred files, and run inside a modpack they cover its modded tools and enchantments too.

### Repair — 68 recipes

59 `smithing_repair`, and 9 `smithing_combine` for the items vanilla gives no material repair
at all. Each repair names the material `Item#isValidRepairItem` already accepts for that item,
at `percent: 25` — the same quarter of maximum durability an anvil restores per unit — and
uses a `c:` tag wherever one exists, so a mod's own iron or leather works too.

### Enchanting — 42 recipes

Vanilla prices enchanting in experience, which looks at first like there is nothing to
translate. But it does carry a number meant for exactly this job: `anvil_cost`, the levels an
anvil bills per level of the enchantment, halved when the source is a book. So that is the
price, one material item per experience level vanilla would have taken.

`anvil_cost` also sorts every vanilla enchantment into four bands, and that sorting is the
game's own opinion about which enchantments are precious — Sharpness sits with Protection
because vanilla put it there, not because we agreed with each other about it. Each band is
paid in its own material:

| `anvil_cost` | Material | Per level | For example |
| --- | --- | --- | --- |
| 1 | `c:gems/lapis` | 1 | Sharpness, Protection, Efficiency, Power, Piercing |
| 2 | `c:gems/amethyst` | 1 | Unbreaking, Smite, Feather Falling, Loyalty |
| 4 | `c:ender_pearls` | 2 | Mending, Fortune, Looting, Fire Aspect, Riptide |
| 8 | `minecraft:blaze_powder` | 4 | Silk Touch, Infinity, Thorns, Channeling, the curses |

So Sharpness V costs 5 lapis, Unbreaking III costs 3 amethyst, Mending costs 2 ender pearls,
and Thorns III costs 12 blaze powder — the same 12 levels an anvil would have charged. Blaze
powder is the one entry written as a plain item: nothing in `c:` names it in 1.21.1, and a
tag that does not exist matches nothing at all rather than complaining.

What each may be applied to is the enchantment's own `supported_items`, so Depth Strider
lands on boots and Breach on maces without anyone listing them.

One rough edge: a book carrying two enchantments matches both their recipes, and the table
picks whichever vanilla found first. Splitting such a book is not something a pack can
express yet.

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
