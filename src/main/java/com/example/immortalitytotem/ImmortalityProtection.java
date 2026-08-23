package com.example.immortalitytotem;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;

/**
 * 所有事件和 Mixin 共用的实时判定。这里故意不缓存：物品移出物品栏后必须立即恢复原版语义。
 */
public final class ImmortalityProtection {
    public static final float MINIMUM_HEALTH = 1.0F;

    private ImmortalityProtection() {
    }

    public static boolean isProtected(LivingEntity entity) {
        return entity instanceof Player player && hasTotem(player);
    }

    public static boolean hasTotem(Player player) {
        // 极早期构造调用通常不会发生；保留 null 防御可避免广泛 Mixin 在异常构造时崩溃。
        Inventory inventory = player.getInventory();
        return inventory != null && inventory.contains(stack ->
                !stack.isEmpty() && stack.is(ImmortalityTotemMod.IMMORTALITY_TOTEM.getKey()));
    }

    public static boolean isInvalidOrBelowFloor(float health) {
        return !Float.isFinite(health) || health < MINIMUM_HEALTH;
    }

    /**
     * 修复可由直接数据改写留下的半死亡状态。setHealth 的 Mixin 会保证即使最大生命异常也写入 1。
     */
    public static boolean stabilize(Player player) {
        if (!hasTotem(player)) {
            return false;
        }

        if (isInvalidOrBelowFloor(player.getHealth())) {
            player.setHealth(MINIMUM_HEALTH);
        }

        player.deathTime = 0;
        if (player.getPose() == Pose.DYING) {
            player.setPose(Pose.STANDING);
        }

        return true;
    }
}
