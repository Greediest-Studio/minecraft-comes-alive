package com.smd.mca.items;

import com.smd.mca.entity.EntityVillagerMCA;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;

public abstract class ItemSpecialCaseGift extends Item {
    public abstract boolean handle(EntityPlayer player, EntityVillagerMCA villager);
}
