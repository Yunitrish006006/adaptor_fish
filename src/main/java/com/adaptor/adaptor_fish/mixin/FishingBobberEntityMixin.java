package com.adaptor.adaptor_fish.mixin;

import com.adaptor.adaptor_fish.FishTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.message.MessageType;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {

    @Shadow
    private boolean caughtFish;

    @Shadow
    public abstract PlayerEntity getPlayerOwner();

    @Shadow
    @Nullable
    public abstract Entity getHookedEntity();

//    @Shadow
//    protected abstract boolean deflectsAgainstWorldBorder();

    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
    private boolean onWorldSpawnEntity(World world, Entity entity) {
        if (!world.isClient() && entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getStack();
            ServerPlayerEntity player = (ServerPlayerEntity) this.getPlayerOwner();
            if (this.caughtFish && this.getHookedEntity() == null && stack.isIn(ItemTags.FISHES)) {
                spawnFish(stack, (ServerWorld) world, (FishingBobberEntity)(Object)this, player);
                return false;
            }
        }
        return world.spawnEntity(entity);
    }

    @Unique
    private void spawnFish(ItemStack stack, ServerWorld world, FishingBobberEntity bobber, ServerPlayerEntity player) {
        spawnLiveFish(world, bobber, player, stack.getItem().getTranslationKey());
    }

    @Unique
    private void spawnLiveFish(ServerWorld world, FishingBobberEntity bobber, ServerPlayerEntity player, String fishType) {
        FishEntity fish = createFish(world, fishType);
        if (fish == null) return;
        fish.setPosition(bobber.getX(), bobber.getY(), bobber.getZ());
        Vec3d direction = player.getEyePos().subtract(fish.getEntityPos()).normalize();
        fish.setAir(fish.getMaxAir());
        world.spawnEntity(fish);

        double initialSpeed = 0.5;
        fish.setVelocity(direction.x * initialSpeed, direction.y * initialSpeed + 0.2, direction.z * initialSpeed);

        FishTracker.trackFish(world, fish, player);
    }

    @Unique
    private FishEntity createFish(ServerWorld world, String fishType) {

        String type = fishType.replace("item.minecraft.", "");

        return switch (type) {
            case "cod" -> new CodEntity(net.minecraft.entity.EntityType.COD, world);
            case "salmon" -> new SalmonEntity(net.minecraft.entity.EntityType.SALMON, world);
            case "tropical_fish" -> new TropicalFishEntity(net.minecraft.entity.EntityType.TROPICAL_FISH, world);
            case "pufferfish" -> new PufferfishEntity(net.minecraft.entity.EntityType.PUFFERFISH, world);
            default -> null;
        };
    }
}

