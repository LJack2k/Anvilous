# Anvilous for pack makers

The short version. [The README](../README.md) has the full field reference.

Anvilous moves repairing, combining and enchanting to the **vanilla smithing table**, paid for
in materials instead of experience. No new blocks, no new menus, nothing to collide with.

---

## 1. Ship the jar

`mods/`, NeoForge 1.21.1, **both sides**. Recipes are synced to the client and the table
filters its input slots against the client's own recipe list, so a client without the mod
can't use it.

## 2. Decide what to keep

The mod is the engine; the recipes are content. The recipe types, the table behaviour and the
JEI/EMI/REI plugins all work with none of the built-ins loaded.

`config/anvilous-common.toml`, shipped with your pack:

```toml
[built_in_recipes]
	repair = true
	combine = true
	enchant = false
```

Three switches, because the three aren't equally opinionated:

- **repair** and **combine** are vanilla's own numbers read back out of the registry — the
  material its anvil already accepts, the quarter it already restores, the pairs it already
  allows. Hard to object to.
- **enchant** prices from `anvil_cost` and then picks a material per band
  (lapis → amethyst → ender pearls → blaze powder). The pricing is vanilla's; the ladder is
  ours. This is the one most likely to cut across your progression.

All three `false` starts you from nothing.

> You don't have to choose all-or-nothing. The built-ins sit at the **bottom** of the pack
> stack, so your own file at the same path just wins. Override the dozen you care about and
> leave the rest. Only switch a whole set off when you're replacing the lot.
>
> There's no way to *delete* a recipe from JSON — that's the whole reason the switches exist.

## 3. Generate your own

Don't hand-write these. Turn the generators on:

```toml
[tools]
	commands = true
```

Then, in your pack, as an operator:

```
/anvilous dump_repair 25
/anvilous dump_combine
/anvilous dump_enchant
```

They read the **loaded registries** — `isValidRepairItem`, `anvil_cost`, `supported_items` —
so they cover every modded tool and enchantment your pack has, not just vanilla's. Output
lands in `<game dir>/anvilous-generated/defaults_<set>/`, laid out as a datapack already.
Copy the folder, edit what you disagree with, ship it.

Inside each set the recipes sit in a folder per namespace — `recipe/minecraft/`,
`recipe/mekanism/`, `recipe/cataclysm/` — so you can read the export, and delete one mod's
worth in one go.

Every non-vanilla recipe comes out with a `neoforge:conditions` block on it, keyed on the item
for repair and combine and on the mod for enchanting. That means **one generated pack is
shippable to all of your packs**: a Mekanism recipe simply does not load where Mekanism is
absent. Generate in your biggest pack and reuse the output in the rest.

The three sets Anvilous ships were made by exactly these commands.

Turn `commands` back off before release. They write files and no player needs them.

## 4. The recipe formats

Three types, all registered under vanilla's smithing recipe type. `base` takes an item or a
tag; `materials` mixes items and tags freely.

```json
{ "type": "anvilous:smithing_repair",
  "base": { "item": "minecraft:diamond_pickaxe" },
  "materials": [ { "tag": "c:gems/diamond", "percent": 25 } ],
  "keep_enchantments": true, "repair_cost": "reset" }
```

```json
{ "type": "anvilous:smithing_combine",
  "base": { "item": "minecraft:bow" }, "repair_cost": "reset" }
```

```json
{ "type": "anvilous:smithing_enchant",
  "enchantment": "minecraft:mending",
  "base": { "tag": "minecraft:enchantable/durability" },
  "materials": [ { "tag": "c:ender_pearls", "per_level": 2 } ],
  "repair_cost": "reset" }
```

`percent` is per material item, for repairs. `per_level` is per enchantment level. The
table's **left slot stays empty** unless you add a `"template"` ingredient.

`"repair_cost": "reset"` clears the hidden anvil counter. That's what stops *Too Expensive!*

## 5. When something doesn't work

```
/anvilous simulate_combine <item> <damage_a> <damage_b> [enchantment] [level]
```

Combining is where "why did nothing happen?" comes from, and the answer is never in the recipe
file — it's the durability sum, the enchantment merge, or no recipe matching at all. This runs
two items through a real `SmithingMenu` and tells you which. Needs a player, so run it in
game, not from console.

For everything else: if you have JEI, EMI or REI in the pack, every recipe shows up there.

---

## Things that will bite you

- **Nine vanilla items have no material repair at all** — bows, crossbows, fishing rods,
  shears, tridents, flint and steel, brushes, and the two sticks. That's vanilla's rule, not
  ours. Combining is their only route.
- **A book with two enchantments** matches both recipes and the table picks whichever vanilla
  found first. Splitting such a book isn't expressible yet.
- **Switching a set off on a world that already had it on** leaves one `Missing data pack`
  warning in the log. Harmless; a world made after the switch never sees it.
- **Materials resolve to `c:` tags** wherever exactly one exists, so another mod's iron ingot
  repairs an iron pickaxe without anyone writing a file. Where no tag fits, the generator
  writes a plain item instead — a tag that doesn't exist matches nothing rather than erroring.
- **A condition that skips a recipe says so only at debug level.** `Skipping loading recipe X
  as its conditions were not met` is a debug line, invisible at the default log level, so a
  recipe that quietly didn't load looks identical to one that isn't there. The visible signal
  is the `Loaded N recipes` count.
- **A recipe naming an item that doesn't exist doesn't crash anything.** It logs `Parsing error
  loading recipe` and is skipped, and the rest of the pack loads. Conditions are there to keep
  the log honest and the counts right, not to prevent a crash.
