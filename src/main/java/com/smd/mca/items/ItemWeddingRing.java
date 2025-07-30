package com.smd.mca.items;

import com.google.common.base.Optional;
import com.smd.mca.core.MCA;
import com.smd.mca.entity.EntityVillagerMCA;
import com.smd.mca.entity.data.PlayerHistory;
import com.smd.mca.entity.data.PlayerSaveData;
import com.smd.mca.enums.EnumDialogueType;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.text.TextComponentString;

public class ItemWeddingRing extends ItemSpecialCaseGift {
    public boolean handle(EntityPlayer player, EntityVillagerMCA villager) {

        if (!MCA.getConfig().allowPlayerMarriage) {
            player.sendMessage(new TextComponentString(I18n.format("gui.marriage.failed")));
            return false;
        }

        PlayerSaveData playerData = PlayerSaveData.get(player);
        PlayerHistory history = villager.getPlayerHistoryFor(player.getUniqueID());
        String response;

        if (villager.isMarriedTo(player.getUniqueID()))
            response = "interaction.marry.fail.marriedtogiver";
        else if (villager.isMarried())
            response = "interaction.marry.fail.marriedtoother";
        else if (playerData.isMarriedOrEngaged())
            response = "interaction.marry.fail.marriedtoother";
        else if (this instanceof ItemEngagementRing && history.getHearts() < MCA.getConfig().marriageHeartsRequirement / 2)
            response = "interaction.marry.fail.lowhearts";
        else if (!(this instanceof ItemEngagementRing) && history.getHearts() < MCA.getConfig().marriageHeartsRequirement)
            response = "interaction.marry.fail.lowhearts";
        else {
            response = "interaction.marry.success";
            playerData.marry(villager.getUniqueID(), villager.get(EntityVillagerMCA.VILLAGER_NAME));
            villager.getPlayerHistoryFor(player.getUniqueID()).setDialogueType(EnumDialogueType.SPOUSE);
            villager.spawnParticles(EnumParticleTypes.HEART);
            villager.marry(player);
        }

        villager.say(Optional.of(player), response);
        return false;
    }
}
