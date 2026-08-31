package com.immortalitytotem;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 永生图腾物品。主手按住右键时开始三秒引导，行为与弓/弩一致：
 * 中途松开右键或切换物品会停止；完整长按 60 tick 才传送。
 */
public final class ImmortalityTotemItem extends Item {
    public ImmortalityTotemItem(Properties properties) {
        super(properties);
    }

    /** 右键空气/空白处：开始长按使用。 */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return startUse(level, player, hand);
    }

    /** 右键方块：同样开始长按使用。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return startUse(context.getLevel(), context.getPlayer(), context.getHand());
    }

    /** 右键生物：同样开始长按使用。 */
    @Override
    public InteractionResult interactLivingEntity(
            ItemStack itemStack,
            Player player,
            LivingEntity target,
            InteractionHand hand
    ) {
        return startUse(player.level(), player, hand);
    }

    /** 引导时长固定为 60 tick，即 3 秒。 */
    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity user) {
        return ImmortalityTotemCasting.DURATION_TICKS;
    }

    /** 使用弓的持握动画，让玩家能直观看到自己在引导。 */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
        return ItemUseAnimation.BOW;
    }

    /** 长按期间周期性播放紫水晶叮铃声。 */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack itemStack, int ticksRemaining) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            ImmortalityTotemCasting.tick(player, ticksRemaining);
        }
    }

    /** 完整长按 60 tick：服务端原地闪光并传送到出生点。 */
    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            ImmortalityTotemCasting.finish(player);
        }
        return itemStack;
    }

    /** 中途松开右键：打断引导，不传送。 */
    @Override
    public boolean releaseUsing(ItemStack itemStack, Level level, LivingEntity entity, int remainingTime) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            ImmortalityTotemCasting.cancel(player);
        }
        return true;
    }

    /** 切换物品、物品被移除或其他停止路径：清理引导效果。 */
    @Override
    public void onStopUsing(ItemStack itemStack, LivingEntity entity, int count) {
        if (!entity.level().isClientSide() && entity instanceof ServerPlayer player) {
            ImmortalityTotemCasting.cancel(player);
        }
    }

    /** 只有继续使用同一个物品栈才视为未切换；换到另一格/另一栈会打断引导。 */
    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        return oldStack == newStack;
    }

    /** 所有右键入口共用：只接受主手，服务端启动使用状态并施加引导效果。 */
    private static InteractionResult startUse(Level level, Player player, InteractionHand hand) {
        // 只处理主手交互；空上下文、旁观者不触发
        if (level == null || player == null || hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
            return InteractionResult.PASS;
        }

        // 引导成功后的物品冷却只限制本轮右键启动
        if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }

        player.startUsingItem(hand);

        // 服务端确认真正开始使用后再加成属性；客户端只负责展示和播放
        if (!level.isClientSide()
                && player instanceof ServerPlayer serverPlayer
                && player.isUsingItem()
                && player.getUsedItemHand() == hand
                && player.getUseItem().is(ImmortalityTotemMod.IMMORTALITY_TOTEM.getKey())) {
            ImmortalityTotemCasting.start(serverPlayer);
        }

        return InteractionResult.CONSUME;
    }
}
