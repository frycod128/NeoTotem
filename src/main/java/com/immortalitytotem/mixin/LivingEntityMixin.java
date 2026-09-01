package com.immortalitytotem.mixin;

import com.immortalitytotem.ImmortalityProtection;
import com.immortalitytotem.ImmortalityTotemMod;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 改写玩家"是否死亡"的核心不变量，同时不影响未携带物品的实体。 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    // 影子字段：同步数据中的生命值槽（直接写它可绕开 setHealth 钳制，用于修正非法值）
    @Shadow @Final private static EntityDataAccessor<Float> DATA_HEALTH_ID;
    // 影子字段：原版"已死"标记
    @Shadow protected boolean dead;

    /** 将 Mixin 视角转回真正的 LivingEntity 实例。 */
    @Unique
    private LivingEntity immortalitytotem$self() {
        return (LivingEntity) (Object) this;
    }

    /** 该实体是否为携带图腾的玩家。 */
    @Unique
    private boolean immortalitytotem$isProtected() {
        return ImmortalityProtection.isProtected(this.immortalitytotem$self());
    }

    /** 清除全部死亡状态：死标记、死亡计时、倒地姿态，并兜底修正生命值。 */
    @Unique
    private void immortalitytotem$clearDeathState() {
        LivingEntity self = this.immortalitytotem$self();
        // 复位"已死"标记
        this.dead = false;
        // 复位死亡动画计时
        self.deathTime = 0;
        // 倒地姿态恢复站立
        if (self.getPose() == Pose.DYING) {
            self.setPose(Pose.STANDING);
        }
        // 生命非法或低于下限时直接写同步数据修正
        if (ImmortalityProtection.isInvalidOrBelowFloor(self.getHealth())) {
            // 直接写同步数据，避免异常 max_health < 1 时递归调用 setHealth。
            self.getEntityData().set(DATA_HEALTH_ID, ImmortalityProtection.MINIMUM_HEALTH);
        }
    }

    /** setHealth 的原版钳制完成后立即修正 0、负数、NaN 和无穷。 */
    @Inject(method = "setHealth", at = @At("TAIL"))
    private void immortalitytotem$enforceHealthFloor(float requestedHealth, CallbackInfo ci) {
        // 只对携带图腾的实体生效
        if (this.immortalitytotem$isProtected()) {
            // 顺带清除任何已存在的死亡状态
            this.immortalitytotem$clearDeathState();
            // 原始入参非法（0/负数/NaN/无穷）时强制回到保底 1
            if (ImmortalityProtection.isInvalidOrBelowFloor(requestedHealth)) {
                // +Infinity 会被原版钳制成 maxHealth；仍须依据原始非法入参强制回到 1。
                this.immortalitytotem$self()
                        .getEntityData()
                        .set(DATA_HEALTH_ID, ImmortalityProtection.MINIMUM_HEALTH);
            }
        }
    }

    /** 拦截 isDeadOrDying() 返回 true：受保护玩家永远不算"垂死"。 */
    @Inject(method = "isDeadOrDying", at = @At("RETURN"), cancellable = true)
    private void immortalitytotem$neverEnterDeathState(CallbackInfoReturnable<Boolean> cir) {
        // 原版已经认为实体健康时不必扫描 43 格物品栏。
        if (cir.getReturnValueZ() && this.immortalitytotem$isProtected()) {
            // 清除死亡状态并把结果改为 false
            this.immortalitytotem$clearDeathState();
            cir.setReturnValue(false);
        }
    }

    /** 拦截 isAlive() 返回 false：受保护玩家保持逻辑存活。 */
    @Inject(method = "isAlive", at = @At("RETURN"), cancellable = true)
    private void immortalitytotem$remainLogicallyAlive(CallbackInfoReturnable<Boolean> cir) {
        // 只在原版判定为"不存活"时介入
        if (!cir.getReturnValueZ() && this.immortalitytotem$isProtected()) {
            LivingEntity self = this.immortalitytotem$self();
            // 清除死亡状态
            this.immortalitytotem$clearDeathState();
            // 已因退出、卸载或切维度移除的旧实例不能被伪装为存活。
            cir.setReturnValue(!self.isRemoved());
        }
    }

    /** 拦截直接调用 kill()：受保护玩家直接忽略。 */
    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreDirectKill(ServerLevel level, CallbackInfo ci) {
        if (this.immortalitytotem$isProtected()) {
            // 清理死亡状态后吞掉本次调用
            this.immortalitytotem$clearDeathState();
            ci.cancel();
        }
    }

    /** 拦截 handleKillingBlow（致死一击逻辑）。 */
    @Inject(method = "handleKillingBlow", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreKillingBlow(CallbackInfo ci) {
        if (this.immortalitytotem$isProtected()) {
            this.immortalitytotem$clearDeathState();
            ci.cancel();
        }
    }

    /** 拦截 tickDeath（死亡逐帧动画逻辑）。 */
    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreDeathTick(CallbackInfo ci) {
        if (this.immortalitytotem$isProtected()) {
            this.immortalitytotem$clearDeathState();
            ci.cancel();
        }
    }

    /** 在 LivingEntity 的死亡效果和 brain 清理发生前阻止 KILLED 移除。 */
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreKilledRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        // 只拦截 KILLED 理由；切维度/登出等其他理由必须放行
        if (reason == Entity.RemovalReason.KILLED && this.immortalitytotem$isProtected()) {
            this.immortalitytotem$clearDeathState();
            ci.cancel();
        }
    }

    /**
     * 非玩家生物携带图腾触发原版死亡保护时，跳过 itemStack.shrink(1)。
     * 复活、粒子、药水效果仍完全走原版流程，只是图腾本身不被消耗。
     */
    @Redirect(
            method = "checkTotemDeathProtection",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"
            )
    )
    private void immortalitytotem$preserveTotemForNonPlayer(ItemStack stack, int amount) {
        LivingEntity self = this.immortalitytotem$self();
        if (self instanceof Player || !stack.is(ImmortalityTotemMod.IMMORTALITY_TOTEM.getKey())) {
            stack.shrink(amount);
        }
    }
}
