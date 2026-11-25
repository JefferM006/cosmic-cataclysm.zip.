package com.cosmiccataclysm;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/** Clic derecho: abre el cielo sobre el lugar al que miras (o 60 bloques delante de ti). */
public class CosmicRemoteItem extends Item {
    public CosmicRemoteItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient) {
            HitResult hit = player.raycast(150.0, 0.0f, false);
            Vec3d target;
            if (hit.getType() == HitResult.Type.MISS) {
                target = player.getPos().add(Vec3d.fromPolar(0, player.getYaw()).multiply(60));
            } else {
                target = hit.getPos();
            }
            CataclysmManager.start((ServerWorld) world, target);
            player.getItemCooldownManager().set(this, 20 * 45);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.cosmic_cataclysm.cosmic_remote.tooltip").formatted(Formatting.LIGHT_PURPLE));
    }
}
