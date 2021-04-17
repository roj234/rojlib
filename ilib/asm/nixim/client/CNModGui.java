/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ilib.asm.nixim.client;

import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.client.GuiScrollingList;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.versioning.ComparableVersion;

import java.awt.*;
import java.awt.image.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/1 15:06
 */
@Nixim("net.minecraftforge.fml.client.GuiModList")
//!!AT ["net.minecraftforge.fml.client.GuiModList$Info", ["init"]]
public class CNModGui extends GuiModList {
    @Shadow("listWidth")
    private int listWidth;
    @Shadow("modInfo")
    private GuiScrollingList modInfo;
    @Shadow("selectedMod")
    private ModContainer selectedMod;
    @Shadow("configModButton")
    private GuiButton configModButton;
    @Shadow("disableModButton")
    private GuiButton disableModButton;

    public CNModGui(GuiScreen mainMenu) {
        super(mainMenu);
    }

    @Inject("updateCache")
    @SuppressWarnings("fallthrough")
    private void updateCache() {
        this.configModButton.visible = false;
        this.modInfo = null;
        if (this.selectedMod != null) {

            final ModMetadata metadata = this.selectedMod.getMetadata();

            ResourceLocation logoPath = null;
            Dimension logoDims = new Dimension(0, 0);
            String logoFile = metadata.logoFile;
            if (!logoFile.isEmpty()) {
                TextureManager tm = this.mc.getTextureManager();
                IResourcePack pack = FMLClientHandler.instance().getResourcePackFor(this.selectedMod.getModId());

                try {
                    BufferedImage logo = null;
                    if (pack != null) {
                        logo = pack.getPackImage();
                    } else {
                        InputStream logoResource = this.getClass().getResourceAsStream(logoFile);
                        if (logoResource != null) {
                            logo = TextureUtil.readBufferedImage(logoResource);
                        }
                    }

                    if (logo != null) {
                        logoPath = tm.getDynamicTextureLocation("modlogo", new DynamicTexture(logo));
                        logoDims = new Dimension(logo.getWidth(), logo.getHeight());
                    }
                } catch (IOException ignored) {
                }
            }

            List<String> lines = new ArrayList<>();

            ForgeVersion.CheckResult result = ForgeVersion.getResult(this.selectedMod);

            lines.add(metadata.name);
            lines.add("版本: " + this.selectedMod.getDisplayVersion() + (this.selectedMod.getVersion().equals(this.selectedMod.getDisplayVersion()) ? "" : "(" + this.selectedMod.getVersion() + ")"));
            lines.add("ID: '" + this.selectedMod.getModId() + '\'');

            if (!metadata.autogenerated) {
                final GuiButton button = this.disableModButton;
                button.packedFGColour = 0;
                button.visible = this.selectedMod.canBeDisabled().ordinal() != 2;
                switch (this.selectedMod.canBeDisabled().ordinal()) {
                    case 1:
                        button.packedFGColour = 16724855;
                    case 0:
                        button.enabled = true;
                        break;
                    case 2:
                    case 3:
                        button.enabled = false;
                        break;
                }

                IModGuiFactory gui = FMLClientHandler.instance().getGuiFactoryFor(this.selectedMod);
                if (gui != null) {
                    this.configModButton.visible = true;
                    this.configModButton.enabled = gui.hasConfigGui();
                } else {
                    this.configModButton.visible = false;
                    this.configModButton.enabled = false;
                }

                if (!metadata.credits.isEmpty()) {
                    lines.add("贡献者: " + metadata.credits);
                }

                lines.add("作者: " + metadata.getAuthorList());
                lines.add("网址: " + metadata.url);
                if (metadata.childMods.isEmpty()) {
                    lines.add("没有子mod");
                } else {
                    lines.add("子mod: " + metadata.getChildModList());
                }

                checkUpdate(lines, result);

                lines.add(null);
                lines.add(metadata.description);
            } else {
                checkUpdate(lines, result);

                lines.add(null);
                lines.add(TextFormatting.RED + " mcmod.info不存在!");
                lines.add(TextFormatting.RED + " 如果你是作者, 请添加上!");
            }


            if (result.changes.size() > 0) {
                switch (result.status.ordinal()) {
                    case 3:
                    case 6:
                        lines.add(null);
                        lines.add("更新内容:");

                        for (Map.Entry<ComparableVersion, String> entry : result.changes.entrySet()) {
                            lines.add("  " + entry.getKey() + ":");
                            lines.add(entry.getValue());
                            lines.add(null);
                        }
                }
            }

            this.modInfo = new GuiModList.Info(this.width - this.listWidth - 30, lines, logoPath, logoDims);
        }
    }

    @Copy
    private static void checkUpdate(List<String> lines, ForgeVersion.CheckResult result) {
        switch (result.status.ordinal()) {
            case 3:
                lines.add("有更新: " + (result.url == null ? "" : result.url));
                break;
            case 6:
                lines.add("有测试版更新: " + (result.url == null ? "" : result.url));
                break;
        }
    }
}
