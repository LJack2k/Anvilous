package nl.ljack2k.anvilous.tools;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import nl.ljack2k.anvilous.Anvilous;
import nl.ljack2k.anvilous.AnvilousConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The half of the dev harness worth shipping: the recipe generators, and the one simulation
 * that answers a question JSON cannot.
 *
 * <p>The generators read the loaded registries rather than a table someone typed, so run
 * inside a modpack they emit correct recipes for <em>its</em> tools and enchantments, not
 * just vanilla's — which is the whole reason they are here. The vanilla sets the mod ships
 * were produced by exactly these commands.
 *
 * <p>Off unless {@code tools.commands} is set in the config — including in dev, so a dev run
 * exercises the same gate a pack maker does. They are a pack-making
 * tool and nothing a player needs: {@code dump_*} writes files into the game directory, and
 * all of them require permission level 2 besides.
 *
 * <p>The rest of the harness — kits, screenshots, the recipe browser — stays in
 * {@code nl.ljack2k.anvilous.dev}, which is stripped from the published jar. Both register
 * under {@code /anvilous}; Brigadier merges the two literal roots.
 */
@EventBusSubscriber(modid = Anvilous.MOD_ID)
public final class PackTools {
    private PackTools() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!AnvilousConfig.toolCommands()) {
            return;
        }

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(Anvilous.MOD_ID)
                .requires(source -> source.hasPermission(2));

        // One generator per pack the mod ships, so what comes out is a folder you can copy
        // over a datapack rather than a pile to sort by filename.
        root.then(Commands.literal("dump_repair")
                .then(Commands.argument("percent", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> dumpRepair(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "percent")))));

        root.then(Commands.literal("dump_combine")
                .executes(ctx -> dumpCombine(ctx.getSource())));

        root.then(Commands.literal("dump_enchant")
                .executes(ctx -> dumpEnchant(ctx.getSource())));

        // Combining is the one that produces "why did nothing happen?", because the answer is
        // never in the recipe file: it is the durability sum, the enchantment merge, or no
        // recipe matching at all. This drives a real SmithingMenu and reports which.
        root.then(Commands.literal("simulate_combine")
                .then(Commands.argument("item", ResourceLocationArgument.id())
                        .then(Commands.argument("damage_a", IntegerArgumentType.integer(0))
                                .then(Commands.argument("damage_b", IntegerArgumentType.integer(0))
                                        .executes(ctx -> simulateCombine(
                                                ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "item"),
                                                IntegerArgumentType.getInteger(ctx, "damage_a"),
                                                IntegerArgumentType.getInteger(ctx, "damage_b"),
                                                null, 0))
                                        // Optional: put the same enchantment on both, to
                                        // exercise the merge rather than only the durability.
                                        .then(Commands.argument("enchantment", ResourceLocationArgument.id())
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                                        .executes(ctx -> simulateCombine(
                                                                ctx.getSource(),
                                                                ResourceLocationArgument.getId(ctx, "item"),
                                                                IntegerArgumentType.getInteger(ctx, "damage_a"),
                                                                IntegerArgumentType.getInteger(ctx, "damage_b"),
                                                                ResourceLocationArgument.getId(ctx, "enchantment"),
                                                                IntegerArgumentType.getInteger(ctx, "level")))))))));

        // The dev harness registers its own /anvilous; Brigadier merges the two literal roots.
        event.getDispatcher().register(root);
        Anvilous.LOGGER.info("Pack-making commands registered: dump_repair, dump_combine, dump_enchant, simulate_combine");
    }

    /**
     * Writes a starter repair pack derived from the game's own data.
     *
     * <p>Vanilla already knows every repair material — {@code Item#isValidRepairItem} is what
     * the anvil asks — so the baseline is read out of the registry rather than typed by hand,
     * which gets the awkward ones right (elytra take phantom membrane, shields take planks)
     * and, run inside a modpack, covers every modded tool in it too.
     *
     * <p>Material repair only; combining is {@link #dumpCombine} now that the two are
     * separate packs and a pack may well want one without the other.
     */
    private static int dumpRepair(CommandSourceStack source, int percent) {
        List<Item> damageable = damageableItems();
        Path out = generated("defaults_repair");
        List<String> noMaterial = new ArrayList<>();
        int written = 0;

        try {
            Files.createDirectories(out);
            for (Item item : damageable) {
                ItemStack tool = new ItemStack(item);
                List<ResourceLocation> materials = BuiltInRegistries.ITEM.stream()
                        .filter(candidate -> item.isValidRepairItem(tool, new ItemStack(candidate)))
                        .map(BuiltInRegistries.ITEM::getKey)
                        .toList();

                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

                if (materials.isEmpty()) {
                    // For these a combine is the only repair there is, which is exactly how
                    // the anvil treats them too.
                    noMaterial.add(id.toString());
                    continue;
                }

                List<String> entries = asTagsWherePossible(materials, percent);

                StringBuilder json = new StringBuilder();
                json.append("{\n").append(itemCondition(id))
                        .append("  \"type\": \"anvilous:smithing_repair\",\n")
                        .append("  \"base\": { \"item\": \"").append(id).append("\" },\n")
                        .append("  \"materials\": [\n");
                for (int i = 0; i < entries.size(); i++) {
                    json.append("    ").append(entries.get(i))
                            .append(i < entries.size() - 1 ? ",\n" : "\n");
                }
                json.append("  ],\n  \"keep_enchantments\": true,\n  \"repair_cost\": \"reset\"\n}\n");

                Files.writeString(recipeFile(out, id, ""), json.toString());
                written++;
            }
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write the pack: " + e.getMessage()));
            return 0;
        }

        Anvilous.LOGGER.info("dump_repair: {} damageable items, {} written, {} with no material repair: {}",
                damageable.size(), written, noMaterial.size(), noMaterial);

        int finalWritten = written;
        source.sendSuccess(() -> Component.literal(
                damageable.size() + " damageable items, " + finalWritten + " repair recipes written to "
                        + out + ", " + noMaterial.size() + " have no material repair"), false);
        return 1;
    }

    /**
     * Writes a combine recipe for every damageable item.
     *
     * <p>Every one of them, not only the ones with no material repair. Vanilla's anvil
     * combines any two of the same damageable item — {@code AnvilMenu} asks nothing but
     * "same item, damageable?" — and combining is not the poor relation of material repair it
     * first looks like. It is the route you have when you own the item and not the material:
     * a damaged pickaxe out of a chest, before there is any ore to mend it with.
     *
     * <p>Filenames are prefixed because an item has both a repair and a combine recipe, and
     * two recipes cannot share a path. The prefix is only a filename — the recipe id it
     * produces is arbitrary, and nothing reads it back.
     */
    private static int dumpCombine(CommandSourceStack source) {
        List<Item> damageable = damageableItems();
        Path out = generated("defaults_combine");
        int written = 0;

        try {
            Files.createDirectories(out);
            for (Item item : damageable) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                Files.writeString(recipeFile(out, id, "combine_"), """
                        {
                        %s  "type": "anvilous:smithing_combine",
                          "base": { "item": "%s" },
                          "repair_cost": "reset"
                        }
                        """.formatted(itemCondition(id), id));
                written++;
            }
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write the pack: " + e.getMessage()));
            return 0;
        }

        Anvilous.LOGGER.info("dump_combine: {} combine recipes written", written);

        int finalWritten = written;
        source.sendSuccess(() -> Component.literal(
                finalWritten + " combine recipes written to " + out), false);
        return 1;
    }

    /** Everything in the registry a durability number applies to, modded items included. */
    private static List<Item> damageableItems() {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> new ItemStack(item).isDamageableItem())
                .toList();
    }

    /** Everything the dump commands write, under one folder in the run directory. */
    private static Path generatedRoot() {
        return FMLPaths.GAMEDIR.get().resolve("anvilous-generated");
    }

    /**
     * Where one generated pack's recipes go, laid out exactly as they sit in the jar so that
     * refreshing the built-ins is a copy of the folder rather than a sort by filename.
     */
    private static Path generated(String pack) {
        return generatedRoot().resolve(pack).resolve("data/anvilous/recipe");
    }

    /**
     * Where one recipe file goes: a folder per namespace, under the pack's recipe root.
     *
     * <p>The namespace is load-bearing here, not decoration. These files were named after the
     * item's path alone, which is unique in vanilla because vanilla is one namespace — but run
     * the generator in a modpack and two mods owning a {@code steel_pickaxe} write the same
     * filename, the second quietly winning. A folder per namespace cannot collide, because the
     * namespace is exactly what makes an item id unique to begin with.
     *
     * <p>It also gives the export a shape a person can work with: one mod's recipes are one
     * folder, to read, keep or delete as a unit.
     */
    private static Path recipeFile(Path packRoot, ResourceLocation id, String prefix) throws IOException {
        Path dir = packRoot.resolve(id.getNamespace());
        Files.createDirectories(dir);
        return dir.resolve(prefix + id.getPath() + ".json");
    }

    /**
     * A condition block making a recipe load only where its subject item exists.
     *
     * <p>NeoForge applies {@code neoforge:conditions} above the recipe serializer dispatch —
     * {@code RecipeManager} decodes through {@code Recipe.CONDITIONAL_CODEC}, which wraps the
     * dispatch — so this works on Anvilous recipe types without a line of code on our side,
     * and the serializers never see the key.
     *
     * <p>Keyed on the item rather than the mod on purpose: the recipe's real dependency is the
     * item, and mods do not keep their items in the jar you would guess. Mekanism's tools are
     * in MekanismTools, Mystical Agriculture's in Agradditions; {@code item_exists} does not
     * care which jar supplied it, and {@code mod_loaded} would get both wrong.
     *
     * <p>Vanilla items get no condition. They are always there, and a condition that can never
     * be false is noise in 68 files.
     */
    private static String itemCondition(ResourceLocation id) {
        if (id.getNamespace().equals("minecraft")) {
            return "";
        }
        return "  \"neoforge:conditions\": [\n"
                + "    { \"type\": \"neoforge:item_exists\", \"item\": \"" + id + "\" }\n"
                + "  ],\n";
    }

    /**
     * The same, for enchantments, which have no {@code enchantment_exists} condition to use.
     *
     * <p>So it falls back to the mod: an enchantment is registered by the mod that declares it,
     * which makes {@code mod_loaded} a fair proxy here even though it would be the wrong tool
     * for items. Vanilla enchantments get no condition, for the reason above.
     */
    private static String modCondition(String namespace) {
        if (namespace.equals("minecraft")) {
            return "";
        }
        return "  \"neoforge:conditions\": [\n"
                + "    { \"type\": \"neoforge:mod_loaded\", \"modid\": \"" + namespace + "\" }\n"
                + "  ],\n";
    }


    /**
     * The material each {@code anvil_cost} band is paid in.
     *
     * <p>Vanilla sorts its enchantments into exactly four bands — 1, 2, 4 and 8 — and that
     * sorting is the game's own opinion about which enchantments are precious. Reusing it
     * means the four materials are not a ladder someone invented: Sharpness sits in the same
     * band as Protection because vanilla put it there.
     */
    private static ResourceLocation enchantTier(int anvilCost) {
        String item = anvilCost <= 1 ? "lapis_lazuli"
                : anvilCost <= 2 ? "amethyst_shard"
                : anvilCost <= 4 ? "ender_pearl"
                : "blaze_powder";
        return ResourceLocation.withDefaultNamespace(item);
    }

    /**
     * A {@code c:} tag holding exactly this one item, if one exists.
     *
     * <p>Exactly, never a superset: {@code c:gems} would also accept diamonds, which is not
     * what the price says. A tag of precisely this item is free to widen — that is where
     * another mod's lapis lands — without changing what the recipe means.
     */
    private static String materialEntry(ResourceLocation item, int perLevel) {
        String source = BuiltInRegistries.ITEM.getTags()
                .filter(pair -> pair.getFirst().location().getNamespace().equals("c"))
                .filter(pair -> pair.getSecond().size() == 1)
                .filter(pair -> pair.getSecond().stream().allMatch(
                        holder -> BuiltInRegistries.ITEM.getKey(holder.value()).equals(item)))
                .map(pair -> "\"tag\": \"" + pair.getFirst().location() + "\"")
                .findFirst()
                .orElse("\"item\": \"" + item + "\"");
        return "{ " + source + ", \"per_level\": " + perLevel + " }";
    }

    /**
     * Writes one enchanting recipe per enchantment, priced from the game's own numbers.
     *
     * <p>There is no vanilla recipe to copy here the way {@code isValidRepairItem} gives one
     * for repair — vanilla charges experience. But it does carry a number for exactly this
     * job: {@code anvil_cost}, the levels an anvil bills per enchantment level, halved for a
     * book. So the price is that same figure, one material item per experience level vanilla
     * would have taken. Sharpness V costs five, Mending costs two, Thorns III costs twelve.
     *
     * <p>Which material comes from the band, and what the recipe may be applied to comes from
     * the enchantment's own {@code supported_items}, so a modded enchantment generates a
     * sensible recipe without anybody writing one.
     */
    private static int dumpEnchant(CommandSourceStack source) {
        var registry = source.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Path out = generated("defaults_enchant");
        List<String> skipped = new ArrayList<>();
        int written = 0;

        try {
            Files.createDirectories(out);
            for (Holder.Reference<Enchantment> holder : registry.listElements().toList()) {
                ResourceLocation id = holder.key().location();
                Enchantment enchantment = holder.value();

                // An inline list of items rather than a tag cannot be written as a base
                // ingredient here, and in vanilla there are none - so say so rather than
                // quietly leaving a hole in the set.
                Optional<TagKey<Item>> baseTag = enchantment.definition().supportedItems().unwrapKey();
                if (baseTag.isEmpty()) {
                    skipped.add(id + " (supported_items is a literal list, not a tag)");
                    continue;
                }

                int anvilCost = enchantment.getAnvilCost();
                int perLevel = Math.max(1, anvilCost / 2);

                Files.writeString(recipeFile(out, id, "enchant_"), """
                        {
                        %s  "type": "anvilous:smithing_enchant",
                          "enchantment": "%s",
                          "base": { "tag": "%s" },
                          "materials": [
                            %s
                          ],
                          "repair_cost": "reset"
                        }
                        """.formatted(modCondition(id.getNamespace()), id, baseTag.get().location(),
                        materialEntry(enchantTier(anvilCost), perLevel)));
                written++;
            }
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write the pack: " + e.getMessage()));
            return 0;
        }

        Anvilous.LOGGER.info("dump_enchant: {} written, {} skipped: {}", written, skipped.size(), skipped);

        int finalWritten = written;
        source.sendSuccess(() -> Component.literal(
                finalWritten + " enchanting recipes written to " + out
                        + ", " + skipped.size() + " skipped"), false);
        return 1;
    }

    /**
     * Rewrites a flat list of repair items as tags wherever a tag covers them exactly.
     *
     * <p>{@code isValidRepairItem} answers per item and knows nothing about tags, so a shield
     * comes back as eleven plank types and an iron tool as one hard-coded {@code iron_ingot}.
     * Written out like that the pack is pinned to whatever happened to be installed when it
     * was generated — the opposite of why tags exist. So a tag is used whenever every item in
     * it is a valid repair material (never a superset, which would accept things the game
     * would not), biggest tag first, and anything left over stays an item.
     */
    private static List<String> asTagsWherePossible(List<ResourceLocation> materials, int percent) {
        Set<ResourceLocation> remaining = new LinkedHashSet<>(materials);
        List<String> entries = new ArrayList<>();

        List<Map.Entry<TagKey<Item>, Set<ResourceLocation>>> usable = BuiltInRegistries.ITEM.getTags()
                .map(pair -> Map.<TagKey<Item>, Set<ResourceLocation>>entry(
                        pair.getFirst(),
                        pair.getSecond().stream()
                                .map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()))
                                .collect(Collectors.toCollection(LinkedHashSet::new))))
                .filter(entry -> !entry.getValue().isEmpty())
                .filter(entry -> remaining.containsAll(entry.getValue()))
                .sorted(Comparator
                        .<Map.Entry<TagKey<Item>, Set<ResourceLocation>>>comparingInt(e -> -e.getValue().size())
                        // Prefer the common namespace: that is the one other mods also populate.
                        .thenComparing(e -> e.getKey().location().getNamespace().equals("c") ? 0 : 1)
                        .thenComparing(e -> e.getKey().location().toString()))
                .collect(Collectors.toCollection(ArrayList::new));

        for (var tag : usable) {
            if (!remaining.containsAll(tag.getValue())) {
                continue;
            }
            remaining.removeAll(tag.getValue());
            entries.add("{ \"tag\": \"" + tag.getKey().location() + "\", \"percent\": " + percent + " }");
        }

        for (ResourceLocation leftover : remaining) {
            entries.add("{ \"item\": \"" + leftover + "\", \"percent\": " + percent + " }");
        }
        return entries;
    }

    /** Combining two of the same item, driven through the same real menu. */
    private static int simulateCombine(CommandSourceStack source, ResourceLocation item,
                                       int damageA, int damageB,
                                       ResourceLocation enchantment, int level)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel serverLevel = player.serverLevel();
        BlockPos pos = player.blockPosition();

        if (!serverLevel.getBlockState(pos).is(Blocks.SMITHING_TABLE)) {
            serverLevel.setBlockAndUpdate(pos, Blocks.SMITHING_TABLE.defaultBlockState());
        }

        Item type = BuiltInRegistries.ITEM.getOptional(item).orElse(null);
        if (type == null) {
            source.sendSuccess(() -> Component.literal("No such item: " + item), false);
            return 0;
        }

        ItemStack toolA = new ItemStack(type);
        ItemStack toolB = new ItemStack(type);
        toolA.setDamageValue(Math.min(damageA, toolA.getMaxDamage()));
        toolB.setDamageValue(Math.min(damageB, toolB.getMaxDamage()));
        int startA = toolA.getDamageValue();
        int startB = toolB.getDamageValue();

        Holder<Enchantment> holder = null;
        if (enchantment != null) {
            holder = serverLevel.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .get(ResourceKey.create(Registries.ENCHANTMENT, enchantment))
                    .orElse(null);
            if (holder == null) {
                source.sendSuccess(() -> Component.literal("No such enchantment: " + enchantment), false);
                return 0;
            }
            for (ItemStack stack : List.of(toolA, toolB)) {
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(
                        stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
                mutable.set(holder, level);
                stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
            }
        }
        Holder<Enchantment> queried = holder;

        SmithingMenu menu = new SmithingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(serverLevel, pos));
        menu.getSlot(SmithingMenu.BASE_SLOT).set(toolA);
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(toolB);
        menu.createResult();

        ItemStack result = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        if (result.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    item.getPath() + " " + startA + " + " + startB + " -> NO RECIPE MATCH"), false);
            return 0;
        }

        menu.getSlot(SmithingMenu.RESULT_SLOT).onTake(player, result);

        int endDamage = result.getDamageValue();
        boolean sacrificeGone = menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty();
        String enchantReport = queried == null ? "" : " | " + enchantment.getPath() + " now level "
                + result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(queried);

        source.sendSuccess(() -> Component.literal(
                item.getPath() + " damage " + startA + " + damage " + startB
                        + " -> damage " + endDamage + "/" + result.getMaxDamage()
                        + " | sacrifice consumed " + sacrificeGone + enchantReport), false);
        return 1;
    }
}
