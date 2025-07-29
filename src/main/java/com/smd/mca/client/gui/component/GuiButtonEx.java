package com.smd.mca.client.gui.component;

import lombok.Getter;
import com.smd.mca.api.types.APIButton;
import com.smd.mca.core.MCA;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class GuiButtonEx extends GuiButton {
    @Getter private APIButton apiButton;

    public GuiButtonEx(GuiScreen gui, APIButton apiButton) {
        super(apiButton.getId(), (gui.width / 2) + apiButton.getX(), (gui.height / 2) + apiButton.getY(), apiButton.getWidth(), apiButton.getHeight(), MCA.getLocalizer().localize(apiButton.getIdentifier()));
        this.apiButton = apiButton;
    }
}
