package com.example.immortalitytotem.mixin;

import com.example.immortalitytotem.ImmortalityProtection;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 改写玩家“是否死亡”的核心不变量，同时不影响未携带物品的实体。 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Shadow @Final private static EntityDataAccessor<Float> DATA_HEALTH_ID;
    @Shadow protected boolean dead;

    @Unique
    private LivingEntity immortalitytotem$self() {
        return (LivingEntity) (Object) this;
    }

    @Unique
    private boolean immortalitytotem$isProtected() {
        return ImmortalityProtection.isProtected(this.immortalitytotem$self());
    }

    @Unique
    private void immortalitytotem$clearDeathState() {
        LivingEntity self = this.immortalitytotem$self();
        this.dead = false;
        self.deathTime = 0;
        if (self.getPose() == Pose.DYING) {
            self.setPose(Pose.STANDING);
        }
        if (ImmortalityProtection.isInvalidOrBelowFloor(self.getHealth())) {
            // 直接写同步数据，避免异常 max_health < 1 时递归调用 setHealth。
            self.getEntityData().set(DATA_HEALTH_ID, ImmortalityProtection.MINIMUM_HEALTH);
        }
    }

    /** setHealth 的原版钳制完成后立即修正 0、负数、NaN 和无穷。 */
    @Inject(method = "setHealth", at = @At("TAIL"))
    private void immortalitytotem$enforceHealthFloor(float requestedHealth, CallbackInfo ci) {
        if (this.immortalitytotem$isProtected()) {
            this.immortalitytotem$clearDeathState();
            if (ImmortalityProtection.isInvalidOrBelowFloor(requestedHealth)) {
                // +Infinity 会被原版钳制成 maxHealth；仍须依据原始非法入参强制回到 1。
                this.immortalitytotem$self()
                        .getEntityData()
                        .set(DATA_HEALTH_ID, ImmortalityProtection.MINIMUM_HEALTH);
            }
        }
    }

    @Inject(method = "isDeadOrDying", at = @At("RETURN"), cancellable = true)
    private void immortalitytotem$neverEnterDeathState(CallbackInfoReturnable<Boolean> cir) {
        // 原版已经认为实体健康时不必扫描 43 格物品栏。
        if (cir.getReturnValueZ() && this.immortalitytotem$isProtected()) {
            this.immortalitytotem$clearDeathState();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isAlive", at = @At("RETURN"), cancellable = true)
    private void immortalitytotem$remainLogicallyAlive(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && this.immortalitytotem$isProtected()) {
            LivingEntity self = this.immortalitytotem$self();
            this.immortalitytotem$clearDeathState();
            // 已因退出、卸载或切维度移除的旧实例不能被伪装为存活。
            cir.setReturnValue(!self.isRemoved());
        }
    }

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreDirectKill(ServerLevel level, CallbackInfo ci) {
        if (this.immortalitytotem$isProtected()) {
            this.immortalitytotem$clearDeathState();
            ci.cancel();
        }
    }

    @Inject(method = "handleKillingBlow", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreKillingBlow(CallbackInfo ci) {
        if (this.immortalitytotem$isProtected()) {
            this.immortalitytotem$clearDeathState();
            ci.cancel();
        }
    }

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
        if (reason == Entity.RemovalReason.KILLED && this.immortalitytotem$isProtected()) {
            this.immortalitytotem$clearDeathState();
            ci.cancel();
        }
    }
}
