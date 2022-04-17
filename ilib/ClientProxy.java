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

package ilib;

import ilib.api.BlockColor;
import ilib.api.ItemColor;
import ilib.api.client.ICustomModel;
import ilib.client.GeneratedModelRepo;
import ilib.client.KeyRegister;
import ilib.client.TextureHelper;
import ilib.client.model.BlockStateBuilder;
import ilib.client.model.ModelInfo;
import ilib.client.renderer.entity.RenderTNTMy;
import ilib.command.MasterCommand;
import ilib.command.sub.MySubs;
import ilib.event.ClientEvent;
import ilib.util.Hook;
import ilib.util.PlayerUtil;
import ilib.util.Registries;
import ilib.util.Tinter;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.color.ItemColors;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import roj.io.IOUtil;

import java.io.IOException;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/23 14:10
 */
public final class ClientProxy extends ServerProxy {
    public static final Minecraft mc = Minecraft.getMinecraft();

    final Thread clientThread = Thread.currentThread();

    @Override
    public boolean isOnThread(boolean client) {
        return Thread.currentThread() == (client ? clientThread : serverThread);
    }

    @Override
    public void runAtMainThread(boolean client, Runnable run) {
        if (isOnThread(client)) run.run();
        else if (client) mc.addScheduledTask(run);
        else PlayerUtil.getMinecraftServer().addScheduledTask(run);
    }

    @Override
    void preInit() {
        super.preInit();

        MinecraftForge.EVENT_BUS.register(this);

        TextureHelper.preInit();

        ClientEvent.init();
    }

    @Override
    void init() {
        super.init();

        RenderManager man = mc.getRenderManager();
        man.entityRenderMap.put(EntityTNTPrimed.class, new RenderTNTMy(man));

        KeyRegister.init();
    }

    @Override
    void postInit() {
        ClientCommandHandler.instance.registerCommand(new MasterCommand("il_client", 0)
                .register(MySubs.DUMP_GL_INFO)
                .register(MySubs.RELOAD_TEXTURE)
                .register(MySubs.PACKET_SIMULATOR)
                .register(MySubs.GC));
    }

    @SubscribeEvent
    public void blockColors(ColorHandlerEvent.Block event) {
        BlockColors handler = event.getBlockColors();
        Tinter blockTinter = new Tinter();
        Block[] colored = new Block[1];
        for (Block block : Registries.block()) {
            if (block instanceof BlockColor) {
                colored[0] = block;
                handler.registerBlockColorHandler(blockTinter, colored);
            }
        }
    }

    @SubscribeEvent
    public void itemColors(ColorHandlerEvent.Item event) {
        ItemColors handler = event.getItemColors();
        Tinter itemTinter = new Tinter();
        Item[] colored = new Item[1];
        for (Item item : Registries.item()) {
            if (item instanceof ItemColor) {
                colored[0] = item;
                handler.registerItemColorHandler(itemTinter, colored);
            }
        }
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        ImpLib.HOOK.triggerOnce(Hook.MODEL_REGISTER);

        for(Block block : Registries.block()) {
            if (block instanceof ICustomModel) ((ICustomModel) block).registerModel();
        }

        for(Item item : Registries.item()) {
            if (item instanceof ICustomModel) ((ICustomModel) item).registerModel();
        }

        List<ModelInfo> models = Registry.getModels();
        for (int i = 0; i < models.size(); i++) {
            models.get(i).apply();
        }

        String blockPath = "assets/" + ImpLib.MODID + "/blockstates/generated/blocks.json";
        GeneratedModelRepo.addModel(blockPath, blockMergedModel.build());
        blockMergedModel = null;

        String itemPath = "assets/" + ImpLib.MODID + "/blockstates/generated/items.json";
        GeneratedModelRepo.addModel(itemPath, itemMergedModel.build());
        itemMergedModel = null;

        String fluidPath = "assets/" + ImpLib.MODID + "/blockstates/generated/fluids.json";
        GeneratedModelRepo.addModel(fluidPath, fluidMergedModel.build());
        fluidMergedModel = null;

        GeneratedModelRepo.addModel();
    }

    private static BlockStateBuilder blockMergedModel;
    private static BlockStateBuilder itemMergedModel;
    private static BlockStateBuilder fluidMergedModel;

    public BlockStateBuilder getBlockMergedModel() {
        return blockMergedModel;
    }
    public BlockStateBuilder getItemMergedModel() {
        return itemMergedModel;
    }
    public BlockStateBuilder getFluidMergedModel() {
        return fluidMergedModel;
    }
    public void registerTexture(ResourceLocation tex) {
        ClientEvent.registerTexture(tex);
    }

    static {
        try {
            itemMergedModel = new BlockStateBuilder(IOUtil.readUTF(ClientProxy.class, "assets/" + ImpLib.MODID + "/blockstates/items.json"), true);
            blockMergedModel = new BlockStateBuilder(IOUtil.readUTF(ClientProxy.class, "assets/" + ImpLib.MODID + "/blockstates/blocks.json"), false);
            fluidMergedModel = new BlockStateBuilder(false).setDefaultModel("forge:fluid");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
