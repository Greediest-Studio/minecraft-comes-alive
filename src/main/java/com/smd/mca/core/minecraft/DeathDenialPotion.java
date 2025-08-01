package com.smd.mca.core.minecraft;

import net.minecraft.potion.Potion;
import net.minecraft.entity.EntityLivingBase;

public class DeathDenialPotion extends Potion {
    public DeathDenialPotion() {
        super(false, 0x550000); // 非正面效果，暗红色
        setPotionName("effect.death_denial");
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return false; // 不需要每tick触发
    }

    @Override
    public void performEffect(EntityLivingBase entity, int amplifier) {
    }
}
