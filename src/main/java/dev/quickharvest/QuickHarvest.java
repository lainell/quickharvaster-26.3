package dev.quickharvest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Right-click a fully grown crop: it is harvested and immediately replanted
 * (one seed / wart is taken from the drops as the "replant cost").
 */
public class QuickHarvest implements ModInitializer {

    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);

            if (!isMatureCrop(state)) {
                return InteractionResult.PASS;
            }

            // Client: just report success so the arm swings; the server does the work.
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.SUCCESS;
            }

            harvestAndReplant(serverLevel, pos, state, player);
            return InteractionResult.SUCCESS;
        });
    }

    private static boolean isMatureCrop(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }
        return false;
    }

    private static BlockState freshState(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.getStateForAge(0);
        }
        return state.setValue(NetherWartBlock.AGE, 0);
    }

    private static void harvestAndReplant(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        ItemStack tool = player.getMainHandItem();
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, tool);

        // Take one seed/wart out of the drops to pay for the replant.
        boolean paid = false;
        for (ItemStack drop : drops) {
            if (!paid && !drop.isEmpty()
                    && (drop.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) || drop.is(Items.NETHER_WART))) {
                drop.shrink(1);
                paid = true;
            }
        }

        // Replant (at age 0) only if we managed to pay; otherwise the crop is simply broken.
        level.setBlock(pos, paid ? freshState(state) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);

        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }

        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
