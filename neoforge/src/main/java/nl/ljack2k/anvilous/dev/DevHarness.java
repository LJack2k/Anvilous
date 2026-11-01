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
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import nl.ljack2k.anvilous.Anvilous;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

        event.registrar("1").playToClient(
                ShowRecipesPayload.TYPE,
                ShowRecipesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> nl.ljack2k.anvilous.client.ClientRecipeBrowser.show(
                                payload.isItem(), payload.what())));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!enabled()) {
            return;
        }

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(Anvilous.MOD_ID)
                .requires(source -> source.hasPermission(2));

        // One kit per thing the mod does, so each can be tried without building the test
        // bench by hand every time. Bare "kit" stays the repair one it always was.
        root.then(Commands.literal("kit")
                .executes(ctx -> kit(ctx.getSource(), "repair"))
                .then(Commands.literal("repair").executes(ctx -> kit(ctx.getSource(), "repair")))
                .then(Commands.literal("combine").executes(ctx -> kit(ctx.getSource(), "combine")))
                .then(Commands.literal("enchant").executes(ctx -> kit(ctx.getSource(), "enchant"))));

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
                                                IntegerArgumentType.getInteger(ctx, "materials"),
                                                ResourceLocation.withDefaultNamespace("diamond"),
                                                ResourceLocation.withDefaultNamespace("diamond_pickaxe")))
                                        // Which material and which tool only became worth
                                        // asking once the shipped recipes stopped all being
                                        // about diamonds and pickaxes.
                                        .then(Commands.argument("material", ResourceLocationArgument.id())
                                                .then(Commands.argument("tool", ResourceLocationArgument.id())
                                                        .executes(ctx -> simulateEnchant(
                                                                ctx.getSource(),
                                                                ResourceLocationArgument.getId(ctx, "enchantment"),
                                                                IntegerArgumentType.getInteger(ctx, "level"),
                                                                IntegerArgumentType.getInteger(ctx, "materials"),
                                                                ResourceLocationArgument.getId(ctx, "material"),
                                                                ResourceLocationArgument.getId(ctx, "tool")))))))));

        // dump_repair, dump_combine, dump_enchant and simulate_combine live in
        // nl.ljack2k.anvilous.tools.PackTools, which ships: the dev property turns them on
        // here the same way the config does for a pack maker.

        // Opens the client's recipe browser on one of our categories, or on everything an
        // item takes part in - so a category can be screenshotted without taking the mouse.
        // Whichever viewer the run was started with answers; see ClientRecipeBrowser.
        root.then(Commands.literal("browse")
                .then(Commands.argument("category", ResourceLocationArgument.id())
                        .executes(ctx -> showRecipes(ctx.getSource(), false,
                                ResourceLocationArgument.getId(ctx, "category")))));

        root.then(Commands.literal("browse_item")
                .then(Commands.argument("item", ResourceLocationArgument.id())
                        .executes(ctx -> showRecipes(ctx.getSource(), true,
                                ResourceLocationArgument.getId(ctx, "item")))));

        event.getDispatcher().register(root);
    }

    /** A worn copy of an item, with {@code percentLeft} of its durability remaining. */
    private static ItemStack worn(net.minecraft.world.level.ItemLike item, int percentLeft) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(stack.getMaxDamage() * (100 - percentLeft) / 100);
        return stack;
    }

    private static Holder<Enchantment> enchantment(CommandSourceStack source, String path) {
        return source.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,
                        ResourceLocation.withDefaultNamespace(path)));
    }

    private static ItemStack with(CommandSourceStack source, ItemStack stack, String path, int level) {
        stack.enchant(enchantment(source, path), level);
        return stack;
    }

    private static ItemStack book(CommandSourceStack source, String path, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        Holder<Enchantment> holder = enchantment(source, path);
        // updateEnchantments puts a book's entry in stored_enchantments by itself.
        EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(holder, level));
        return book;
    }

    /**
     * Fills the player's inventory with a bench for one of the three things the mod does.
     *
     * <p>Each kit is chosen to make a rule visible rather than to be a tidy set: the repair
     * one carries an enchanted pickaxe because surviving enchantments is the point, and a
     * nearly full one because taking only what is needed is the other point.
     */
    private static int kit(CommandSourceStack source, String which)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<ItemStack> kit = new ArrayList<>();
        String summary;

        switch (which) {
            case "combine" -> {
                // Two of each, worn to 40%, so the sum plus the 12% bonus lands above half
                // and the bar visibly jumps. One of each pair is enchanted: a combine should
                // carry those across, and Power and Flame should both survive the same craft.
                kit.add(with(source, worn(Items.BOW, 40), "power", 3));
                kit.add(with(source, worn(Items.BOW, 40), "flame", 1));
                kit.add(with(source, worn(Items.TRIDENT, 40), "loyalty", 3));
                kit.add(worn(Items.TRIDENT, 40));
                kit.add(worn(Items.SHEARS, 30));
                kit.add(worn(Items.SHEARS, 30));
                kit.add(worn(Items.FISHING_ROD, 50));
                kit.add(worn(Items.FISHING_ROD, 50));
                summary = "Combining: pairs of bow, trident, shears and rod. "
                        + "The bows carry Power III and Flame - one craft should keep both.";
            }
            case "enchant" -> {
                // One book from each anvil_cost band, so all four materials get used, plus a
                // sword already at Sharpness IV: a second IV book should make it V.
                kit.add(book(source, "sharpness", 5));
                kit.add(book(source, "sharpness", 4));
                kit.add(book(source, "mending", 1));
                kit.add(book(source, "unbreaking", 3));
                kit.add(book(source, "silk_touch", 1));
                kit.add(book(source, "protection", 4));

                kit.add(new ItemStack(Items.DIAMOND_PICKAXE));
                kit.add(with(source, new ItemStack(Items.DIAMOND_SWORD), "sharpness", 4));
                kit.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
                kit.add(new ItemStack(Items.MACE));

                kit.add(new ItemStack(Items.LAPIS_LAZULI, 64));
                kit.add(new ItemStack(Items.AMETHYST_SHARD, 64));
                kit.add(new ItemStack(Items.ENDER_PEARL, 64));
                kit.add(new ItemStack(Items.BLAZE_POWDER, 64));
                summary = "Enchanting: a book from each cost band and all four materials. "
                        + "The sword is already Sharpness IV - the IV book should make it V.";
            }
            default -> {
                kit.add(with(source, with(source, worn(Items.DIAMOND_PICKAXE, 10),
                        "efficiency", 5), "unbreaking", 3));
                // Nearly full, to show a craft taking one diamond rather than the stack.
                kit.add(worn(Items.DIAMOND_PICKAXE, 99));
                kit.add(worn(Items.IRON_SWORD, 20));
                kit.add(worn(Items.LEATHER_CHESTPLATE, 15));
                kit.add(worn(Items.SHIELD, 20));
                kit.add(worn(Items.ELYTRA, 10));

                kit.add(new ItemStack(Items.DIAMOND, 64));
                kit.add(new ItemStack(Items.IRON_INGOT, 64));
                kit.add(new ItemStack(Items.LEATHER, 64));
                kit.add(new ItemStack(Items.OAK_PLANKS, 64));
                kit.add(new ItemStack(Items.PHANTOM_MEMBRANE, 64));
                summary = "Repair: worn gear covering an item, a c: tag and a vanilla tag. "
                        + "The enchanted pickaxe should keep both enchantments; "
                        + "the nearly full one should cost a single diamond.";
            }
        }

        kit.forEach(stack -> player.getInventory().add(stack));

        String message = summary;
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int showRecipes(CommandSourceStack source, boolean isItem, ResourceLocation what) {
        PacketDistributor.sendToAllPlayers(new ShowRecipesPayload(isItem, what));
        source.sendSuccess(() -> Component.literal("Recipe browser opened on " + what), false);
        return 1;
    }



    /** The enchanting half of {@link #simulate}, driven through the same real menu. */
    private static int simulateEnchant(CommandSourceStack source, ResourceLocation enchantment,
                                       int level, int materials,
                                       ResourceLocation material, ResourceLocation tool)
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

        Item materialItem = BuiltInRegistries.ITEM.getOptional(material).orElse(null);
        Item toolItem = BuiltInRegistries.ITEM.getOptional(tool).orElse(null);
        if (materialItem == null || toolItem == null) {
            source.sendSuccess(() -> Component.literal(
                    "No such item: " + (materialItem == null ? material : tool)), false);
            return 0;
        }

        String label = enchantment.getPath() + " " + level + " on " + tool.getPath()
                + " + " + materials + " " + material.getPath();

        SmithingMenu menu = new SmithingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(serverLevel, pos));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(book);
        menu.getSlot(SmithingMenu.BASE_SLOT).set(new ItemStack(toolItem));
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(materialItem, materials));
        menu.createResult();

        ItemStack result = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        if (result.isEmpty()) {
            source.sendSuccess(() -> Component.literal(label + " -> NO RECIPE MATCH"), false);
            return 0;
        }

        menu.getSlot(SmithingMenu.RESULT_SLOT).onTake(player, result);

        int got = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder);
        int used = materials - menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount();
        boolean bookGone = menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty();

        source.sendSuccess(() -> Component.literal(
                label + " -> got level " + got
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
