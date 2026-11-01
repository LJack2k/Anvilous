package nl.ljack2k.anvilous.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import nl.ljack2k.anvilous.Anvilous;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dev-only test harness — active only when {@code -Danvilous.devHarness} is set, which the
 * {@code runServer} / {@code runClientJoin} Gradle runs do and a real install never does.
 *
 * <p>It gives the dev world the conditions a repair test wants and nothing else: flat, lit,
 * dry and empty. The world generation side of that lives in {@code run-server/server.properties}
 * (superflat, peaceful, no mob/animal/villager spawning); the parts that are world state
 * rather than server config are applied here on every server start, so deleting the world
 * folder never loses them.
 *
 * <ul>
 *   <li>{@code /anvilous kit} — a damaged diamond pickaxe and a stack of diamonds.</li>
 *   <li>{@code /anvilous table} — a smithing table at the player's feet.</li>
 *   <li>{@code /anvilous shot} — screenshot whatever the joined client is rendering.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Anvilous.MOD_ID)
public final class DevHarness {
    private DevHarness() {
    }

    /** Set by the runServer / runClientJoin Gradle runs, and by nothing else. */
    private static final String ENABLED_PROPERTY = "anvilous.devHarness";

    /** Noon, so the test bench is lit without the sun moving across screenshots. */
    private static final long FIXED_TIME = 6000L;

    private static boolean enabled() {
        return System.getProperty(ENABLED_PROPERTY) != null;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!enabled()) {
            return;
        }

        MinecraftServer server = event.getServer();
        GameRules rules = server.getGameRules();
        // Daylight and weather are world state, not server.properties, so they have to be
        // set here or every fresh dev world starts cycling again.
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        rules.getRule(GameRules.RULE_DOFIRETICK).set(false, server);
        rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);

        for (ServerLevel level : server.getAllLevels()) {
            level.setDayTime(FIXED_TIME);
            // stopWeather clears whatever the level was loaded with; the gamerule above
            // only stops it changing from here on.
            level.setWeatherParameters(Integer.MAX_VALUE, 0, false, false);
        }

        Anvilous.LOGGER.info("Dev harness: world fixed to noon, clear, no spawning.");
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        if (!enabled()) {
            return;
        }
        event.registrar("1").playToClient(
                ScreenshotRequestPayload.TYPE,
                ScreenshotRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> nl.ljack2k.anvilous.client.ClientScreenshot.take()));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!enabled()) {
            return;
        }

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(Anvilous.MOD_ID)
                .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("kit").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            // Most of the way to broken, so a 25% repair is obvious at a glance.
            pickaxe.setDamageValue(pickaxe.getMaxDamage() - 100);
            player.getInventory().add(pickaxe);
            player.getInventory().add(new ItemStack(Items.DIAMOND, 64));

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Gave a diamond pickaxe at " + 100 + "/" + new ItemStack(Items.DIAMOND_PICKAXE).getMaxDamage()
                            + " durability, and 64 diamonds."), false);
            return 1;
        }));

        root.then(Commands.literal("table").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            player.level().setBlockAndUpdate(player.blockPosition(), Blocks.SMITHING_TABLE.defaultBlockState());
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Smithing table placed at your feet."), false);
            return 1;
        }));

        // The consumption rule lives in SmithingMenu.onTake, which a player normally
        // reaches by clicking the result slot. This drives that exact path - a real
        // SmithingMenu, a real onTake - so the result is the shipped behaviour and not
        // a reimplementation of it that could agree with a bug.
        root.then(Commands.literal("simulate")
                .then(Commands.argument("damage", IntegerArgumentType.integer(0))
                        .then(Commands.argument("materials", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> simulate(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "damage"),
                                        IntegerArgumentType.getInteger(ctx, "materials"))))));

        root.then(Commands.literal("shot").executes(ctx -> {
            PacketDistributor.sendToAllPlayers(new ScreenshotRequestPayload());
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Screenshot requested."), false);
            return 1;
        }));

        root.then(Commands.literal("inspect").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ItemStack held = player.getMainHandItem();
            int repairCost = held.getOrDefault(DataComponents.REPAIR_COST, 0);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    held.getItem() + ": damage " + held.getDamageValue() + "/" + held.getMaxDamage()
                            + ", repair_cost " + repairCost
                            + ", enchantments " + held.getEnchantments().size()), false);
            return 1;
        }));

        // Same idea as simulate, for the enchanting side: a book in the template slot, a tool
        // in the base slot, materials standing in for the experience.
        root.then(Commands.literal("simulate_enchant")
                .then(Commands.argument("enchantment", ResourceLocationArgument.id())
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                .then(Commands.argument("materials", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> simulateEnchant(
                                                ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "enchantment"),
                                                IntegerArgumentType.getInteger(ctx, "level"),
                                                IntegerArgumentType.getInteger(ctx, "materials")))))));

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

        root.then(Commands.literal("dump_repair")
                .then(Commands.argument("percent", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> dumpRepair(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "percent")))));

        event.getDispatcher().register(root);
    }

    /**
     * Writes a starter repair pack derived from the game's own data.
     *
     * <p>Vanilla already knows every repair material — {@code Item#isValidRepairItem} is what
     * the anvil asks — so the baseline is read out of the registry rather than typed by hand,
     * which gets the awkward ones right (elytra take phantom membrane, shields take planks)
     * and, run inside a modpack, covers every modded tool in it too.
     *
     * <p>Items with no material repair at all are reported rather than written: in vanilla
     * those can only be fixed by combining two of them, which this mod does not do yet.
     */
    private static int dumpRepair(CommandSourceStack source, int percent) {
        List<Item> damageable = BuiltInRegistries.ITEM.stream()
                .filter(item -> new ItemStack(item).isDamageableItem())
                .toList();

        Path out = FMLPaths.GAMEDIR.get().resolve("anvilous-generated/data/anvilous/recipe");
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
                    // No material repair exists for these, so combining two is the only way
                    // to fix them - which is exactly what the anvil does with them.
                    noMaterial.add(id.toString());
                    Files.writeString(out.resolve(id.getPath() + ".json"), """
                            {
                              "type": "anvilous:smithing_combine",
                              "base": { "item": "%s" },
                              "repair_cost": "reset"
                            }
                            """.formatted(id));
                    written++;
                    continue;
                }

                List<String> entries = asTagsWherePossible(materials, percent);

                StringBuilder json = new StringBuilder();
                json.append("{\n  \"type\": \"anvilous:smithing_repair\",\n")
                        .append("  \"base\": { \"item\": \"").append(id).append("\" },\n")
                        .append("  \"materials\": [\n");
                for (int i = 0; i < entries.size(); i++) {
                    json.append("    ").append(entries.get(i))
                            .append(i < entries.size() - 1 ? ",\n" : "\n");
                }
                json.append("  ],\n  \"keep_enchantments\": true,\n  \"repair_cost\": \"reset\"\n}\n");

                Files.writeString(out.resolve(id.getPath() + ".json"), json.toString());
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
                damageable.size() + " damageable items, " + finalWritten + " recipes written to "
                        + out + ", " + noMaterial.size() + " have no material repair"), false);
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

    /** The enchanting half of {@link #simulate}, driven through the same real menu. */
    private static int simulateEnchant(CommandSourceStack source, ResourceLocation enchantment,
                                       int level, int materials)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel serverLevel = player.serverLevel();
        BlockPos pos = player.blockPosition();

        if (!serverLevel.getBlockState(pos).is(Blocks.SMITHING_TABLE)) {
            serverLevel.setBlockAndUpdate(pos, Blocks.SMITHING_TABLE.defaultBlockState());
        }

        Holder<Enchantment> holder = serverLevel.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(ResourceKey.create(Registries.ENCHANTMENT, enchantment))
                .orElse(null);
        if (holder == null) {
            source.sendSuccess(() -> Component.literal("No such enchantment: " + enchantment), false);
            return 0;
        }

        // updateEnchantments already routes a book to stored_enchantments - its
        // getComponentType switches on ENCHANTED_BOOK - so copying between components here
        // would overwrite what it just wrote with an empty one.
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(holder, level));

        SmithingMenu menu = new SmithingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(serverLevel, pos));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(book);
        menu.getSlot(SmithingMenu.BASE_SLOT).set(new ItemStack(Items.DIAMOND_PICKAXE));
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DIAMOND, materials));
        menu.createResult();

        ItemStack result = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        if (result.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    enchantment.getPath() + " " + level + " + " + materials
                            + " diamond(s) -> NO RECIPE MATCH"), false);
            return 0;
        }

        menu.getSlot(SmithingMenu.RESULT_SLOT).onTake(player, result);

        int got = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder);
        int used = materials - menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount();
        boolean bookGone = menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty();

        source.sendSuccess(() -> Component.literal(
                enchantment.getPath() + " " + level + " + " + materials + " diamond(s)"
                        + " -> got level " + got
                        + " | consumed " + used
                        + " | book consumed " + bookGone), false);
        return 1;
    }

    /**
     * Runs one repair through a real {@link SmithingMenu} and reports what it cost.
     *
     * <p>Puts a diamond pickaxe at {@code damage} in the base slot and {@code materials}
     * diamonds in the addition slot, asks the menu to build its result, then takes that
     * result exactly as clicking it would. What is left in the addition slot afterwards is
     * the consumption rule, measured rather than assumed.
     */
    private static int simulate(CommandSourceStack source, int damage, int materials)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        // The menu's stillValid check wants a real smithing table under the access.
        if (!level.getBlockState(pos).is(Blocks.SMITHING_TABLE)) {
            level.setBlockAndUpdate(pos, Blocks.SMITHING_TABLE.defaultBlockState());
        }

        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        tool.setDamageValue(Math.min(damage, tool.getMaxDamage()));
        int startDamage = tool.getDamageValue();

        SmithingMenu menu = new SmithingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(level, pos));
        menu.getSlot(SmithingMenu.BASE_SLOT).set(tool);
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DIAMOND, materials));
        menu.createResult();

        ItemStack result = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        if (result.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "damage " + startDamage + " + " + materials + " diamond(s) -> NO RECIPE MATCH"), false);
            return 0;
        }

        // This is the click.
        menu.getSlot(SmithingMenu.RESULT_SLOT).onTake(player, result);

        int endDamage = result.getDamageValue();
        int left = menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount();
        int used = materials - left;
        int restored = startDamage - endDamage;

        source.sendSuccess(() -> Component.literal(
                "damage " + startDamage + " + " + materials + " diamond(s)"
                        + " -> damage " + endDamage
                        + " | restored " + restored
                        + " | consumed " + used
                        + " | repair_cost " + result.getOrDefault(DataComponents.REPAIR_COST, 0)), false);
        return 1;
    }
}
