package com.smd.mca.core;

import com.smd.mca.api.API;
import com.smd.mca.command.CommandAdminMCA;
import com.smd.mca.command.CommandMCA;
import com.smd.mca.core.forge.EventHooks;
import com.smd.mca.core.forge.GuiHandler;
import com.smd.mca.core.forge.NetMCA;
import com.smd.mca.core.forge.ServerProxy;
import com.smd.mca.core.minecraft.ItemsMCA;
import com.smd.mca.core.minecraft.ProfessionsMCA;
import com.smd.mca.core.minecraft.RoseGoldOreGenerator;
import com.smd.mca.entity.EntityGrimReaper;
import com.smd.mca.entity.EntityVillagerMCA;
import com.smd.mca.entity.LifeLinkManager;
import com.smd.mca.enums.EnumGender;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.Logger;

import java.util.*;

@Mod(modid = MCA.MODID, name = MCA.NAME, version = MCA.VERSION, guiFactory = "com.smd.mca.client.MCAGuiFactory")
public class MCA {
    public static final String MODID = "mca";
    public static final String NAME = "Minecraft Comes Alive";
    public static final String VERSION = "6.0.1";
    @SidedProxy(clientSide = "com.smd.mca.core.forge.ClientProxy",
                serverSide = "com.smd.mca.core.forge.ServerProxy")
    public static ServerProxy proxy;
    public static CreativeTabs creativeTab;
    @Mod.Instance
    private static MCA instance;
    private static Logger logger;
    private static Localizer localizer;
    private static Config config;

    public static Logger getLog() {
        return logger;
    }

    public static MCA getInstance() {
        return instance;
    }

    public static Localizer getLocalizer() {
        return localizer;
    }

    public static Config getConfig() {
        return config;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        instance = this;
        logger = event.getModLog();
        proxy.registerEntityRenderers();
        localizer = new Localizer();
        config = new Config(event);
        creativeTab = new CreativeTabs("MCA") {
            @Override
            public ItemStack createIcon() {
                return new ItemStack(ItemsMCA.ENGAGEMENT_RING);
            }
        };
        MinecraftForge.EVENT_BUS.register(new EventHooks());
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
        NetMCA.registerMessages();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        GameRegistry.registerWorldGenerator(new RoseGoldOreGenerator(), MCA.getConfig().roseGoldSpawnWeight);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "EntityVillagerMCA"), EntityVillagerMCA.class, EntityVillagerMCA.class.getSimpleName(), 1120, this, 50, 2, true);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "GrimReaperMCA"), EntityGrimReaper.class, EntityGrimReaper.class.getSimpleName(), 1121, this, 50, 2, true);
        ProfessionsMCA.registerCareers();

        LifeLinkManager.init();

        proxy.registerModelMeshers();
        ItemsMCA.assignCreativeTabs();
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        API.init();
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandMCA());
        event.registerServerCommand(new CommandAdminMCA());
    }

    public String getRandomSupporter() {
            return API.getRandomName(EnumGender.getRandom());
    }
}
