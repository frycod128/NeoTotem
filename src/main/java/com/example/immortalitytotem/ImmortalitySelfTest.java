package com.example.immortalitytotem;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** 仅由开发运行系统属性启用的轻量运行期回归测试。 */
final class ImmortalitySelfTest {
    private ImmortalitySelfTest() {
    }

    static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        ItemStack totem = ImmortalityTotemMod.IMMORTALITY_TOTEM.toStack();
        player.getInventory().setItem(0, totem);

        require(ImmortalityProtection.hasTotem(player), "inventory scan did not find the totem");
        require(
                DeathProtection.TOTEM_OF_UNDYING.equals(totem.get(DataComponents.DEATH_PROTECTION)),
                "vanilla death protection component is missing"
        );
        ResourceKey<Recipe<?>> recipeKey = ResourceKey.create(
                Registries.RECIPE,
                Identifier.fromNamespaceAndPath(ImmortalityTotemMod.MODID, "immortality_totem")
        );
        require(level.recipeAccess().byKey(recipeKey).isPresent(), "nine-totem recipe was not loaded");

        assertHealthSanitized(player, 0.0F);
        assertHealthSanitized(player, -20.0F);
        assertHealthSanitized(player, Float.NaN);
        assertHealthSanitized(player, Float.POSITIVE_INFINITY);
        assertHealthSanitized(player, Float.NEGATIVE_INFINITY);

        player.setPose(Pose.DYING);
        player.setHealth(0.0F);
        require(player.getPose() != Pose.DYING, "dying pose was not repaired");
        require(!player.isDeadOrDying(), "protected player is deadOrDying");
        require(player.isAlive(), "protected player is not logically alive");

        player.setInvulnerable(false);
        player.getAbilities().invulnerable = false;
        player.connection.markClientLoaded();
        player.invulnerableTime = 0;
        player.setHealth(player.getMaxHealth());
        DamageSource normalSource = player.damageSources().generic();
        boolean normalDamageAccepted = player.hurtServer(level, normalSource, 1000.0F);
        require(
                normalDamageAccepted,
                "normal damage was unexpectedly cancelled"
                        + " [entityInvulnerable=" + player.isInvulnerableTo(level, normalSource)
                        + ", abilityInvulnerable=" + player.getAbilities().invulnerable
                        + ", clientLoaded=" + player.connection.hasClientLoaded()
                        + ", health=" + player.getHealth() + "]"
        );
        require(player.getHealth() == 1.0F, "normal lethal damage did not stop at one health");
        require(
                !player.hurtServer(level, player.damageSources().genericKill(), Float.MAX_VALUE),
                "direct generic_kill damage was not rejected"
        );
        require(player.getHealth() == 1.0F, "generic_kill changed protected health");

        ServerPlayer directPlayer = new ServerPlayer(
                server,
                level,
                new GameProfile(UUID.fromString("47ad6a12-cbd8-4f11-b1bd-1650feb48fd0"), "[ImmortalityDirectTest]"),
                ClientInformation.createDefault()
        );
        directPlayer.getInventory().setItem(0, ImmortalityTotemMod.IMMORTALITY_TOTEM.toStack());
        directPlayer.setHealth(1.0F);

        directPlayer.kill(level);
        require(!directPlayer.isRemoved() && directPlayer.getHealth() == 1.0F, "direct kill was not blocked");

        DamageSource source = directPlayer.damageSources().generic();
        directPlayer.die(source);
        require(!directPlayer.isRemoved() && directPlayer.getHealth() == 1.0F, "direct ServerPlayer.die was not blocked");

        directPlayer.remove(Entity.RemovalReason.KILLED);
        require(!directPlayer.isRemoved(), "remove(KILLED) was not blocked");
        directPlayer.setRemoved(Entity.RemovalReason.KILLED);
        require(!directPlayer.isRemoved(), "setRemoved(KILLED) was not blocked");

        DeathEventProbe probe = new DeathEventProbe(directPlayer);
        NeoForge.EVENT_BUS.register(probe);
        boolean cancelled;
        try {
            cancelled = CommonHooks.onLivingDeath(directPlayer, source);
        } finally {
            NeoForge.EVENT_BUS.unregister(probe);
        }
        require(cancelled, "death hook was not short-circuited");
        require(!probe.fired, "LivingDeathEvent was posted");

        require(
                server.getPlayerList().respawn(directPlayer, false, Entity.RemovalReason.KILLED) == directPlayer,
                "KILLED respawn created a replacement player"
        );

        // 最后一个图腾离开后，setHealth 必须原样恢复为原版 0 下限。
        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.setHealth(0.0F);
        require(player.getHealth() == 0.0F, "vanilla health behavior did not return after removal");
        require(player.isDeadOrDying(), "vanilla deadOrDying predicate did not return after removal");
        require(!player.isAlive(), "vanilla isAlive predicate did not return after removal");
        player.setHealth(player.getMaxHealth());

        // 非死亡生命周期移除必须始终放行，否则会破坏维度切换和登出。
        FakePlayer lifecyclePlayer = new FakePlayer(
                level,
                new GameProfile(UUID.fromString("9d7debbc-cfe8-46d7-9935-799c118428c9"), "[ImmortalityLifecycleTest]")
        );
        lifecyclePlayer.getInventory().setItem(0, ImmortalityTotemMod.IMMORTALITY_TOTEM.toStack());
        lifecyclePlayer.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
        require(lifecyclePlayer.isRemoved(), "CHANGED_DIMENSION removal was incorrectly blocked");

        ImmortalityTotemMod.LOGGER.info("Immortality Totem runtime self-test passed");
    }

    private static void assertHealthSanitized(FakePlayer player, float value) {
        player.setHealth(value);
        require(player.getHealth() == ImmortalityProtection.MINIMUM_HEALTH,
                "health was not sanitized for input " + value + ": " + player.getHealth());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Immortality Totem self-test failed: " + message);
        }
    }

    private static final class DeathEventProbe {
        private final ServerPlayer player;
        private boolean fired;

        private DeathEventProbe(ServerPlayer player) {
            this.player = player;
        }

        @SubscribeEvent
        public void onDeath(LivingDeathEvent event) {
            if (event.getEntity() == this.player) {
                this.fired = true;
            }
        }
    }
}
