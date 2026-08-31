package com.example.immortalitytotem;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 所有事件和 Mixin 共用的实时判定。这里故意不缓存：物品移出物品栏后必须立即恢复原版语义。
 */
public final class ImmortalityProtection {
    /** 受保护玩家的生命值下限：至少保留 1 点生命。 */
    public static final float MINIMUM_HEALTH = 1.0F;

    // 工具类：禁止实例化
    private ImmortalityProtection() {
    }

    /** 通用判断：实体是否是一个携带图腾的玩家。 */
    public static boolean isProtected(LivingEntity entity) {
        // 先缩小为 Player 再检查物品栏，非玩家实体一律不受保护
        return entity instanceof Player player && hasTotem(player);
    }

    /** 原版游标槽位号（字符串名 "player.cursor"）；Player 未公开该常量，SlotRanges 与 getSlot 均使用字面值 499。 */
    private static final int CARRY_SLOT = 499;

    /** 检查玩家是否持有永生图腾：物品栏、游标槽、合成格或末影箱。 */
    public static boolean hasTotem(Player player) {
        // 极早期构造调用通常不会发生；保留 null 防御可避免广泛 Mixin 在异常构造时崩溃。
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return false;
        }
        // 白名单一：物品栏（主背包 36 格 + 盔甲 4 格 + 副手 1 格）任一格持有图腾
        if (inventory.contains(ImmortalityProtection::isImmortalityTotem)) {
            return true;
        }
        // 白名单二：游标槽——创造模式下关闭物品栏后鼠标仍悬停持有的物品
        if (hasTotemInSlots(player, CARRY_SLOT, 1)) {
            return true;
        }
        // 白名单三：背包界面的 2x2 合成格（原版槽位 500-503，字符串名 "player.crafting"）
        if (hasTotemInSlots(player, Player.CRAFTING_SLOT_OFFSET, 4)) {
            return true;
        }
        // 白名单四：末影箱（原版槽位 200-226，字符串名 "enderchest"）
        return hasTotemInSlots(player, Player.ENDER_SLOT_OFFSET,
                player.getEnderChestInventory().getContainerSize());
    }

    /** 遍历一段槽位号（走 Player.getSlot 的统一映射：游标/合成格/末影箱/物品栏），任一槽持有图腾即返回 true。 */
    private static boolean hasTotemInSlots(Player player, int startSlot, int count) {
        for (int i = 0; i < count; i++) {
            SlotAccess access = player.getSlot(startSlot + i);
            if (access != null && isImmortalityTotem(access.get())) {
                return true;
            }
        }
        return false;
    }

    /** 判断一个物品栈是否为永生图腾（非空且物品 ID 匹配）。 */
    private static boolean isImmortalityTotem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ImmortalityTotemMod.IMMORTALITY_TOTEM.getKey());
    }

    /** 生命值是否非法（NaN/无穷）或低于保底 1 点。 */
    public static boolean isInvalidOrBelowFloor(float health) {
        return !Float.isFinite(health) || health < MINIMUM_HEALTH;
    }

    /**
     * 修复可由直接数据改写留下的半死亡状态。setHealth 的 Mixin 会保证即使最大生命异常也写入 1。
     *
     * @return 该玩家是否处于受保护状态（调用方据此决定是否继续自己的逻辑）
     */
    public static boolean stabilize(Player player) {
        // 没带图腾：不干预，恢复原版语义
        if (!hasTotem(player)) {
            return false;
        }

        // 生命非法或低于下限：强制写回 1 点生命
        if (isInvalidOrBelowFloor(player.getHealth())) {
            player.setHealth(MINIMUM_HEALTH);
        }

        // 清除死亡动画计时（deathTime），玩家不再播放倒地死亡动画
        player.deathTime = 0;
        // 若处于倒地姿态则恢复站立姿态
        if (player.getPose() == Pose.DYING) {
            player.setPose(Pose.STANDING);
        }

        // 返回 true：表示该玩家确实受保护，本次干预完成
        return true;
    }
}
