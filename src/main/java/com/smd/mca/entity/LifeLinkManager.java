// LifeLinkManager.java
package com.smd.mca.entity;

import com.smd.mca.Tags;
import com.smd.mca.core.minecraft.ModPotion;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.*;

public enum LifeLinkManager {
    INSTANCE;

    private static class LifeLinkBinding {
        private final WeakReference<EntityLivingBase> boss;
        private final WeakReference<EntityPlayer> player;
        private long endTime; // 绑定的结束时间（世界时间）

        public LifeLinkBinding(EntityLivingBase boss, EntityPlayer player, long duration) {
            this.boss = new WeakReference<>(boss);
            this.player = new WeakReference<>(player);
            this.endTime = boss.world.getTotalWorldTime() + duration;
        }

        public boolean isExpired(long currentTime) {
            return currentTime >= endTime;
        }

        public EntityLivingBase getBoss() {
            return boss.get();
        }

        public EntityPlayer getPlayer() {
            return player.get();
        }
    }

    private final Map<UUID, LifeLinkBinding> playerBindings = new HashMap<>();
    private final Map<UUID, UUID> bossToPlayerMap = new HashMap<>();

    public static void init() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    public void addBinding(EntityLivingBase boss, EntityPlayer player, long durationTicks) {
        UUID bossId = boss.getUniqueID();
        UUID playerId = player.getUniqueID();

        removeBindingByBoss(bossId);
        removeBindingByPlayer(playerId);

        LifeLinkBinding binding = new LifeLinkBinding(boss, player, durationTicks);
        playerBindings.put(playerId, binding);
        bossToPlayerMap.put(bossId, playerId);
    }

    public EntityLivingBase getBossForPlayer(EntityPlayer player, long currentTime) {
        LifeLinkBinding binding = playerBindings.get(player.getUniqueID());
        if (binding == null) return null;

        if (binding.isExpired(currentTime) ||
                binding.getBoss() == null ||
                binding.getPlayer() == null) {
            removeBindingByPlayer(player.getUniqueID());
            return null;
        }

        return binding.getBoss();
    }

    public EntityPlayer getPlayerForBoss(EntityLivingBase boss) {
        UUID playerId = bossToPlayerMap.get(boss.getUniqueID());
        if (playerId == null) return null;

        LifeLinkBinding binding = playerBindings.get(playerId);
        if (binding == null || binding.getPlayer() == null) {
            removeBindingByBoss(boss.getUniqueID());
            return null;
        }

        return binding.getPlayer();
    }

    public void removeBindingByBoss(UUID bossId) {
        UUID playerId = bossToPlayerMap.remove(bossId);
        if (playerId != null) {
            playerBindings.remove(playerId);
        }
    }

    public void removeBindingByPlayer(UUID playerId) {
        LifeLinkBinding binding = playerBindings.remove(playerId);
        if (binding != null && binding.getBoss() != null) {
            bossToPlayerMap.remove(binding.getBoss().getUniqueID());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            long currentTime = 0;
            Iterator<Map.Entry<UUID, LifeLinkBinding>> iterator = playerBindings.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<UUID, LifeLinkBinding> entry = iterator.next();
                LifeLinkBinding binding = entry.getValue();

                if (binding.getBoss() != null && currentTime == 0) {
                    currentTime = binding.getBoss().world.getTotalWorldTime();
                }

                if (currentTime > 0 &&
                        (binding.isExpired(currentTime) ||
                                binding.getBoss() == null ||
                                binding.getPlayer() == null)) {

                    if (binding.getPlayer() != null) {
                        binding.getPlayer().sendMessage(new TextComponentString("生命链接已断开"));
                    }
                    iterator.remove();
                    if (binding.getBoss() != null) {
                        bossToPlayerMap.remove(binding.getBoss().getUniqueID());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerHeal(LivingHealEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        World world = player.world;
        if (world.isRemote) return;

        long currentTime = world.getTotalWorldTime();
        EntityLivingBase boss = getBossForPlayer(player, currentTime);

        float healAmount = event.getAmount();
        float futureHealth = player.getHealth() + healAmount;
        float maxHealth = player.getMaxHealth();

        if (boss != null && !boss.isDead && boss.getHealth() < boss.getMaxHealth()) {
            boss.heal(healAmount);
        }

        if (futureHealth > maxHealth) {
            PotionEffect current = player.getActivePotionEffect(ModPotion.DEATH_DENIAL);
            int amplifier = current != null ? Math.min(current.getAmplifier() + 1, 19) : 0;

            PotionEffect effect = new PotionEffect(ModPotion.DEATH_DENIAL, 600, amplifier);
            effect.setCurativeItems(new ArrayList<>());
            player.addPotionEffect(effect);

            world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_WITHER_HURT, SoundCategory.HOSTILE, 1.0F, 0.8F);
        }
        PotionEffect denial = player.getActivePotionEffect(ModPotion.DEATH_DENIAL);
        if (denial != null) {
            float reduction = (denial.getAmplifier() + 1) * 0.05f;
            healAmount *= (1.0f - reduction);
            event.setAmount(healAmount);
        }
    }





    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();
        World world = player.world;
        long currentTime = world.getTotalWorldTime();
        EntityLivingBase boss = getBossForPlayer(player, currentTime);

        if (boss != null && !boss.isDead) {
            // 15%伤害转化为治疗
            float healAmount = event.getAmount() * 0.15f;
            boss.heal(healAmount);
        }
    }
}


