package com.adaptor.adaptor_fish.mixin;

import net.minecraft.entity.passive.*;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {

    @Shadow
    private boolean caughtFish;

    @Shadow
    public abstract PlayerEntity getPlayerOwner();

    @Inject(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"), cancellable = false)
    private void onSpawnLoot(ItemStack usedItem, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        FishingBobberEntity bobber = (FishingBobberEntity) (Object) this;


        if (!bobber.getEntityWorld().isClient() && this.caughtFish) {
            PlayerEntity player = this.getPlayerOwner();
            if (player != null && bobber.getEntityWorld() instanceof ServerWorld serverWorld) {
                spawnLiveFish(serverWorld, bobber, player);
            }
        }
    }

    private void spawnLiveFish(ServerWorld world, FishingBobberEntity bobber, PlayerEntity player) {
        FishEntity fish = createRandomFish(world);
        fish.setPosition(bobber.getX(), bobber.getY(), bobber.getZ());
        Vec3d direction = player.getEyePos().subtract(fish.getEntityPos()).normalize();
        double speed = 0.6;
        fish.setVelocity(direction.x * speed, direction.y * speed + 0.3, direction.z * speed);
        fish.setAir(fish.getMaxAir());
        world.spawnEntity(fish);
    }

    private FishEntity createRandomFish(ServerWorld world) {
        double random = world.getRandom().nextDouble();

        if (random < 0.6) {
            return new CodEntity(net.minecraft.entity.EntityType.COD, world);
        } else if (random < 0.85) {
            return new SalmonEntity(net.minecraft.entity.EntityType.SALMON, world);
        } else if (random < 0.98) {
            return new TropicalFishEntity(net.minecraft.entity.EntityType.TROPICAL_FISH, world);
        } else {
            return new PufferfishEntity(net.minecraft.entity.EntityType.PUFFERFISH, world);
        }
    }
}

