# Store page copy

The text for the CurseForge and Modrinth project pages. Kept here so it is versioned
alongside the logos and cannot quietly drift from what the mod actually does — the pages
themselves are edited by hand, so update this first and paste it across.

---

## Summary (the one-line field)

> Repair, combine and enchant at the smithing table, paid for in materials instead of experience.

*(109 characters — Modrinth allows 256, CurseForge is happier short.)*

---

## Project page body

The rest of this file is the page itself. Paste from the line below.

---

Anvilous moves repairing, combining and enchanting off the anvil and onto the **smithing
table**, where they cost materials instead of experience — and where nothing gets more
expensive the more you use it.

No new blocks, no new menus. It uses the vanilla smithing table, so it cannot collide with
whatever else your pack installs.

## What it does

| Anvil operation | Anvilous |
| --- | --- |
| Repair with material units | Yes, at the smithing table |
| Combine two of the same item | Yes |
| Apply an enchanted book | Yes |
| Rename | No — the smithing table has no text field |

**Repairing** takes a worn tool and a material, and restores a set percentage of the item's
maximum durability per material item. It takes only as many as the repair needs: drop a stack
of diamonds beside a nearly broken pickaxe and it comes out full having spent four; do the
same beside one a single point from full and it spends one.

**Combining** adds two worn copies of an item together, plus vanilla's 12% bonus, and merges
their enchantments — the only repair some items have.

**Enchanting** applies a book to a tool for a price in materials. The price is per level, so
Sharpness I and Sharpness V do not cost the same thing.

Enchantments and components survive all of it untouched, and the hidden anvil-use counter
that eventually produces *Too Expensive!* is reset rather than raised.

## It works out of the box

169 recipes ship enabled as three built-in datapacks, covering **every damageable item and
every enchantment in vanilla**.

They are not a new balance. Every number in them was read out of the game rather than decided
by us:

- Each repair uses the material vanilla's own anvil already accepts for that item, at 25% per
  unit — the same quarter an anvil restores. Nine items have no material repair at all (bows,
  crossbows, fishing rods, shears, tridents, flint and steel, brushes, the two sticks), so for
  those combining is the only route, exactly as the anvil treats them.
- Every damageable item can be combined, not only those nine, because the anvil combines any
  two of the same item — and combining is what you have when you own the tool and not the
  material, like a damaged pickaxe out of a chest before you have found any diamonds.
- Each enchantment is priced from its `anvil_cost`: the levels an anvil bills per level of the
  enchantment, halved for a book. One material item per experience level vanilla would have
  taken. Sharpness V costs 5, Mending costs 2, Thorns III costs 12.

`anvil_cost` also sorts vanilla's enchantments into four bands, which is the game's own
opinion about which are precious, so each band is paid in its own material: lapis, amethyst,
ender pearls, blaze powder.

Materials resolve to `c:` tags wherever one exists, so another mod's iron ingot repairs an
iron pickaxe without anyone writing a file.

## For pack makers

Every recipe is a datapack file, and a pack overrides any of the built-in ones by shipping a
file at the same path. Three types, all registered under vanilla's smithing recipe type:

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

Materials can be named by tag or by item and mixed freely in one recipe, each with its own
percentage. `anvilous:smithing_combine` and `anvilous:smithing_enchant` work the same way;
the README on GitHub documents every field.

And a pack can start from nothing. The built-ins are three datapacks — repair, combine and
enchanting — each switchable off in `config/anvilous-common.toml`, so a pack ships the engine
with none of our balance, or keeps repair and combining (which are vanilla's own numbers) while
dropping the enchanting ladder (which is an opinion we invented). A single world can drop one
with `/datapack disable` or from the world-creation screen instead.

The generators that produced those sets ship too, behind a config switch: turn on
`[tools] commands` and `/anvilous dump_repair`, `dump_combine` and `dump_enchant` write a
ready-to-copy datapack covering every tool and enchantment **your pack** has loaded, modded
ones included, read straight out of the registries. `simulate_combine` answers the one
question a recipe file cannot — why those two items did not merge.

## Recipe viewers

Recipes show up in **JEI**, **EMI** and **REI**, each through a plugin of its own. None is
required — install any, all, or none.

## Requirements

**Minecraft 1.21.1 · NeoForge · needed on both client and server.**

Recipes are synced to every client, and the smithing table filters its input slots against
the client's own recipe list, so a client without the mod cannot use it. In single player
that is nothing to think about; on a server, everyone needs it.
