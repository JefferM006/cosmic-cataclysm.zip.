package com.cosmiccataclysm;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.Vec3d;

public class CosmicCataclysm implements ModInitializer {
    public static final String MOD_ID = "cosmic_cataclysm";

    public static final Item COSMIC_REMOTE = Registry.register(
            Registries.ITEM, Identifier.of(MOD_ID, "cosmic_remote"),
            new CosmicRemoteItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

    public static final EntityType<MeteorEntity> METEOR = Registry.register(
            Registries.ENTITY_TYPE, Identifier.of(MOD_ID, "meteor"),
            FabricEntityTypeBuilder.<MeteorEntity>create(SpawnGroup.MISC, MeteorEntity::new)
                    .dimensions(EntityDimensions.fixed(2.0f, 2.0f))
                    .trackRangeBlocks(256)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .fireImmune()
                    .build());

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(CataclysmPayload.ID, CataclysmPayload.CODEC);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(COSMIC_REMOTE));

        CataclysmManager.init();

        // /cataclysm            -> sobre ti
        // /cataclysm <x y z>    -> sobre esa posición
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("cataclysm")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> run(ctx, ctx.getSource().getPosition()))
                        .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                                .executes(ctx -> run(ctx,
                                        Vec3ArgumentType.getPosArgument(ctx, "pos").getPos(ctx.getSource()))))));
    }

    private static int run(CommandContext<ServerCommandSource> ctx, Vec3d pos) {
        ServerCommandSource src = ctx.getSource();
        CataclysmManager.start(src.getWorld(), pos);
        src.sendFeedback(() -> Text.translatable("commands.cosmic_cataclysm.started"), true);
        return 1;
    }
}
