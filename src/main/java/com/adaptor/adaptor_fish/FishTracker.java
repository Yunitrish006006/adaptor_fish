package com.adaptor.adaptor_fish;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.*;

public class FishTracker {
    private static final List<TrackedFish> trackedFishes = new LinkedList<>();
    private static final Random RANDOM = Random.create();

    static {
        ServerTickEvents.END_WORLD_TICK.register(FishTracker::onWorldTick);
    }

    public static void trackFish(ServerWorld world, FishEntity fish, ServerPlayerEntity player) {
        trackedFishes.add(new TrackedFish(world, fish, player, 100));
    }

    private static void onWorldTick(ServerWorld world) {
        // ==========================
        // Part 1: 處理被追蹤的魚
        // ==========================
        Iterator<TrackedFish> iterator = trackedFishes.iterator();
        while (iterator.hasNext()) {
            TrackedFish tracked = iterator.next();
            if (tracked.world != world) continue;

            FishEntity fish = tracked.fish;
            ServerPlayerEntity player = tracked.player;

            if (!fish.isAlive()) {
                iterator.remove();
                continue;
            }

            // 玩家不在 / 死亡 → 遊走
            if (!player.isAlive()) {
                startWandering(fish);
                iterator.remove();
                continue;
            }

            // 🐟 碰撞箱碰撞檢測
            if (player.getBoundingBox().intersects(fish.getBoundingBox())) {
                collectFish(world, player, fish);
                iterator.remove();
                continue;
            }

            Vec3d dir = player.getEyePos().subtract(fish.getEntityPos());
            double distance = dir.length();

            // 🧲 距離 < 3 停止吸引，只遊走
            if (distance < 3.0) {
                wanderNearPlayer(fish);
            } else {
                // 🧲 拉力控制
                dir = dir.normalize();
                double pull = MathHelper.clamp(distance * 0.4, 0.02, 0.09);
                Vec3d newVel = fish.getVelocity().add(dir.multiply(pull));
                if (fish.isInFluid()) {
                    newVel = newVel.add(0,0.05,0);
                }
                else {
                    newVel = newVel.add(0,0.015,0);
                }
                newVel = newVel.add(randomSwimDirection().multiply(0.01));
                fish.setVelocity(newVel);
                fish.velocityModified = true;
            }

            // 超時 → 遊走
            if (--tracked.ticksLeft <= 0) {
                startWandering(fish);
                iterator.remove();
            }
        }

        // ==========================
        // Part 2: 檢查所有魚與玩家碰撞（碰撞箱檢測）
        // ==========================
        for (FishEntity fish : world.getEntitiesByClass(FishEntity.class, world.getWorldBorder().asVoxelShape().getBoundingBox(), LivingEntity::isAlive)) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.getBoundingBox().intersects(fish.getBoundingBox())) {
                    collectFish(world, player, fish);
                    break; // 避免重複撿取
                }
            }
        }
    }

    /**
     * 🐠 魚被玩家撿起的處理邏輯
     */
    private static void collectFish(ServerWorld world, ServerPlayerEntity player, FishEntity fish) {
        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.4F, 1.7F);
        String fishName = Arrays.stream(fish.getType().getTranslationKey().split("\\.")).toList().getLast();
        switch (fishName) {
            case "salmon" -> player.giveItemStack(new ItemStack(Items.SALMON));
            case "cod" -> player.giveItemStack(new ItemStack(Items.COD));
            case "tropical_fish" -> player.giveItemStack(new ItemStack(Items.TROPICAL_FISH));
            case "pufferfish" -> player.giveItemStack(new ItemStack(Items.PUFFERFISH));
            case "glow_squid" -> player.giveItemStack(new ItemStack(Items.GLOW_INK_SAC));
            default -> player.sendMessage(Text.literal("Fished " + fishName), false);
        }

        fish.discard();
    }

    /**
     * 魚進入遊走狀態
     */
    private static void startWandering(FishEntity fish) {
        fish.setNoGravity(false);
        fish.setVelocity(randomSwimDirection().multiply(0.1));
        fish.velocityModified = true;

        if (fish.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.BUBBLE,
                    fish.getX(), fish.getY(), fish.getZ(),
                    5, 0.1, 0.1, 0.1, 0.02);
        }
    }

    /**
     * 玩家附近的隨機遊走
     */
    private static void wanderNearPlayer(FishEntity fish) {
        Vec3d randomDir = randomSwimDirection().multiply(0.05);
        fish.setVelocity(fish.getVelocity().add(randomDir));
        fish.velocityModified = true;
    }

    private static Vec3d randomSwimDirection() {
        double yaw = MathHelper.nextDouble(RANDOM, 0, 2 * Math.PI);
        double pitch = MathHelper.nextDouble(RANDOM, -0.25, 0.25);
        double x = Math.cos(yaw) * Math.cos(pitch);
        double y = Math.sin(pitch);
        double z = Math.sin(yaw) * Math.cos(pitch);
        return new Vec3d(x, y, z).normalize();
    }

    private static class TrackedFish {
        final ServerWorld world;
        final FishEntity fish;
        final ServerPlayerEntity player;
        int ticksLeft;

        TrackedFish(ServerWorld world, FishEntity fish, ServerPlayerEntity player, int ticksLeft) {
            this.world = world;
            this.fish = fish;
            this.player = player;
            this.ticksLeft = ticksLeft;
        }
    }
}
