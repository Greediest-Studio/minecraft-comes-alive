package com.smd.mca.entity.inventory;

import com.smd.mca.entity.EntityVillagerMCA;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;

public class InventoryMCA extends InventoryBasic {
    private final EntityVillagerMCA villager;

    public InventoryMCA(EntityVillagerMCA villager) {
        super("Villager Inventory", true, 27);
        this.villager = villager;
    }

    public int getFirstSlotContainingItem(Item item) {
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    public boolean contains(Class<?> clazz) {
        for (int i = 0; i < getSizeInventory(); i++) {
            Item item = getStackInSlot(i).getItem();
            if (item.getClass() == clazz) {
                return true;
            }
        }
        return false;
    }

    public ItemStack getBestItemOfType(@Nullable Class<?> type) {
        int slot = getBestItemOfTypeSlot(type);
        return slot >= 0 ? getStackInSlot(slot) : ItemStack.EMPTY;
    }

    public int getBestItemOfTypeSlot(@Nullable Class<?> type) {
        if (type == null) return -1;

        int best = -1;
        int highestDamage = -1;

        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = getStackInSlot(i);
            Item item = stack.getItem();

            if (item.getClass() == type) {
                int damage = stack.getMaxDamage();
                if (damage > highestDamage) {
                    best = i;
                    highestDamage = damage;
                }
            }
        }

        return best;
    }

    public ItemStack getBestArmorOfType(EntityEquipmentSlot slot) {
        ItemStack bestArmor = ItemStack.EMPTY;
        int maxDamage = -1;

        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemArmor) {
                ItemArmor armor = (ItemArmor) stack.getItem();
                if (armor.armorType == slot) {
                    int damage = stack.getMaxDamage();
                    if (damage > maxDamage) {
                        bestArmor = stack;
                        maxDamage = damage;
                    }
                }
            }
        }
        return bestArmor;
    }

    public void dropAllItems() {
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty()) {
                villager.entityDropItem(stack, 1.0F);
            }
        }
    }

    public void readInventoryFromNBT(NBTTagList tagList) {
        for (int i = 0; i < getSizeInventory(); i++) {
            setInventorySlotContents(i, ItemStack.EMPTY);
        }

        for (int i = 0; i < tagList.tagCount(); i++) {
            NBTTagCompound nbt = tagList.getCompoundTagAt(i);
            int slot = nbt.getByte("Slot") & 255;
            if (slot < getSizeInventory()) {
                setInventorySlotContents(slot, new ItemStack(nbt));
            }
        }
    }

    public NBTTagList writeInventoryToNBT() {
        NBTTagList tagList = new NBTTagList();

        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty()) {
                NBTTagCompound nbt = new NBTTagCompound();
                nbt.setByte("Slot", (byte) i);
                stack.writeToNBT(nbt);
                tagList.appendTag(nbt);
            }
        }
        return tagList;
    }
}