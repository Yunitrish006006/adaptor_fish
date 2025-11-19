package com.adaptor.adaptor_fish;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 處理鋤頭右鍵快速收穫並重新種植成熟農作物
 */
public class CropHarvestHelper {

    public static void register() {
        UseBlockCallback.EVENT.register(CropHarvestHelper::onUseBlock);
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        ItemStack heldItem = player.getStackInHand(hand);

        // 檢查是否手持鋤頭
        if (!(heldItem.getItem() instanceof HoeItem)) {
            return ActionResult.PASS;
        }

        BlockPos centerPos = hitResult.getBlockPos();
        BlockState centerState = world.getBlockState(centerPos);

        // 檢查是否為可收穫的農作物
        if (!isMatureCrop(centerState)) {
            return ActionResult.PASS;
        }

        // 只在服務端執行
        if (world instanceof ServerWorld) {
            // 獲取鋤頭的收穫範圍
            double radius = getHarvestRadius(heldItem);

            // 收穫範圍內的所有成熟農作物（使用圓形範圍）
            int harvestedCount = 0;
            int radiusCeil = (int) Math.ceil(radius);

            for (int x = -radiusCeil; x <= radiusCeil; x++) {
                for (int z = -radiusCeil; z <= radiusCeil; z++) {
                    // 計算距離，使用圓形範圍而不是方形
                    double distance = Math.sqrt(x * x + z * z);

                    // 只處理在圓形範圍內的方塊
                    if (distance <= radius) {
                        BlockPos pos = centerPos.add(x, 0, z);
                        BlockState state = world.getBlockState(pos);

                        if (isMatureCrop(state)) {
                            harvestAndReplant(world, pos, state, state.getBlock(), player);
                            harvestedCount++;
                        }
                    }
                }
            }

            // 只有成功收穫時才消耗耐久
            if (harvestedCount > 0 && !player.isCreative()) {
                damageHoe(heldItem, player, world, harvestedCount);
            }
        }

        return ActionResult.SUCCESS;
    }

    /**
     * 檢查農作物是否成熟
     */
    private static boolean isMatureCrop(BlockState state) {
        Block block = state.getBlock();

        // 小麥、胡蘿蔔、馬鈴薯、甜菜根
        if (block instanceof CropBlock cropBlock) {
            return cropBlock.isMature(state);
        }

        // 地獄疙瘩
        if (block instanceof NetherWartBlock) {
            return state.get(NetherWartBlock.AGE) >= 3;
        }

        // 可可豆
        if (block instanceof CocoaBlock) {
            return state.get(CocoaBlock.AGE) >= 2;
        }

        return false;
    }

    /**
     * 收穫並重新種植農作物
     */
    private static void harvestAndReplant(World world, BlockPos pos, BlockState state, Block block, PlayerEntity player) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        // 獲取掉落物
        List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, world.getBlockEntity(pos), player, player.getMainHandStack());

        // 給予玩家掉落物
        boolean hasSeeds = false;
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                // 如果是種子，保留一個用於重新種植
                if (isSeedItem(drop, block) && !hasSeeds) {
                    hasSeeds = true;
                    if (drop.getCount() > 1) {
                        ItemStack remaining = drop.copy();
                        remaining.decrement(1);
                        player.giveItemStack(remaining);
                    }
                } else {
                    player.giveItemStack(drop.copy());
                }
            }
        }

        // 播放收穫音效
        world.playSound(null, pos, SoundEvents.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 1.0F, 1.0F);

        // 重新種植
        if (hasSeeds) {
            BlockState newState = getInitialCropState(block, state);
            world.setBlockState(pos, newState, Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 0.8F, 1.2F);
        } else {
            // 如果沒有種子（不太可能），移除方塊
            world.breakBlock(pos, false);
        }
    }

    /**
     * 檢查物品是否為該作物的種子
     */
    private static boolean isSeedItem(ItemStack stack, Block cropBlock) {
        if (cropBlock == Blocks.WHEAT) {
            return stack.isOf(Items.WHEAT_SEEDS);
        } else if (cropBlock == Blocks.CARROTS) {
            return stack.isOf(Items.CARROT);
        } else if (cropBlock == Blocks.POTATOES) {
            return stack.isOf(Items.POTATO);
        } else if (cropBlock == Blocks.BEETROOTS) {
            return stack.isOf(Items.BEETROOT_SEEDS);
        } else if (cropBlock == Blocks.NETHER_WART) {
            return stack.isOf(Items.NETHER_WART);
        } else if (cropBlock == Blocks.COCOA) {
            return stack.isOf(Items.COCOA_BEANS);
        }
        return false;
    }

    /**
     * 獲取農作物的初始生長狀態
     */
    private static BlockState getInitialCropState(Block block, BlockState originalState) {
        if (block instanceof CropBlock) {
            return block.getDefaultState();
        } else if (block instanceof NetherWartBlock) {
            return Blocks.NETHER_WART.getDefaultState();
        } else if (block instanceof CocoaBlock) {
            // 可可豆需要保持方向
            return Blocks.COCOA.getDefaultState()
                    .with(CocoaBlock.FACING, originalState.get(CocoaBlock.FACING))
                    .with(CocoaBlock.AGE, 0);
        }
        return block.getDefaultState();
    }

    /**
     * 根據鋤頭類型獲取收穫範圍半徑（圓形範圍）
     * 木鋤: 0 (單格)
     * 石鋤: 1.5 (圓形約7格)
     * 鐵鋤: 2.5 (圓形約17格)
     * 金鋤: 1.5 (圓形約7格)
     * 鑽石鋤: 3.5 (圓形約37格)
     * 獄髓鋤: 4.5 (圓形約61格)
     */
    private static double getHarvestRadius(ItemStack hoeStack) {
        if (hoeStack.isOf(Items.WOODEN_HOE)) {
            return 0; // 單格
        } else if (hoeStack.isOf(Items.STONE_HOE)) {
            return 1.5; // 小圓形
        } else if (hoeStack.isOf(Items.IRON_HOE)) {
            return 2.5; // 中圓形
        } else if (hoeStack.isOf(Items.GOLDEN_HOE)) {
            return 1.5; // 小圓形
        } else if (hoeStack.isOf(Items.DIAMOND_HOE)) {
            return 3.5; // 大圓形
        } else if (hoeStack.isOf(Items.NETHERITE_HOE)) {
            return 4.5; // 超大圓形
        }

        return 0; // 默認單格
    }

    /**
     * 損耗鋤頭耐久度
     * 根據收穫數量決定耐久消耗（每3個作物消耗1點耐久）
     */
    private static void damageHoe(ItemStack hoe, PlayerEntity player, World world, int harvestedCount) {
        if (!hoe.isDamageable()) {
            return;
        }

        // 每收穫3個作物消耗1點耐久，至少消耗1點
        int damageAmount = Math.max(1, harvestedCount / 3);

        hoe.setDamage(hoe.getDamage() + damageAmount);

        if (hoe.getDamage() >= hoe.getMaxDamage()) {
            hoe.decrement(1);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
    }
}

