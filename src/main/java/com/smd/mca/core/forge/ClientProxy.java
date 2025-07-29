package com.smd.mca.core.forge;

import com.smd.mca.client.render.RenderReaperFactory;
import com.smd.mca.client.render.RenderVillagerFactory;
import com.smd.mca.core.minecraft.BlocksMCA;
import com.smd.mca.core.minecraft.ItemsMCA;
import com.smd.mca.entity.EntityGrimReaper;
import com.smd.mca.entity.EntityVillagerMCA;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ClientProxy extends ServerProxy {
    @Override
    public void registerEntityRenderers() {
        RenderingRegistry.registerEntityRenderingHandler(EntityVillagerMCA.class, RenderVillagerFactory.INSTANCE);
        RenderingRegistry.registerEntityRenderingHandler(EntityGrimReaper.class, RenderReaperFactory.INSTANCE);
    }

    @Override
    public void registerModelMeshers() {
        ItemsMCA.registerModelMeshers();
        BlocksMCA.registerModelMeshers();
    }
}
