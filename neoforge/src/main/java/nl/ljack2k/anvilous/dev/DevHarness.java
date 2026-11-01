package nl.ljack2k.anvilous.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import nl.ljack2k.anvilous.Anvilous;

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

        event.getDispatcher().register(root);
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
