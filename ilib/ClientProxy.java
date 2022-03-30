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
import ilib.client.GeneratedModelRepo;
import ilib.client.KeyRegister;
import ilib.client.TextureHelper;
import ilib.client.model.BlockModelInfo;
import ilib.client.model.BlockStateBuilder;
import ilib.client.model.ItemModelInfo;
import ilib.client.model.ModelInfo;
import ilib.client.renderer.entity.RenderLightningBoltMI;
import ilib.client.renderer.entity.RenderTNTMy;
import ilib.client.renderer.mirror.MirrorSubSystem;
import ilib.command.MasterCommand;
import ilib.command.sub.CommandPixelPainting;
import ilib.command.sub.MySubs;
import ilib.entity.EntityLightningBoltMI;
import ilib.event.ClientEvent;
import ilib.util.*;
import roj.config.data.CMapping;
import roj.io.IOUtil;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/23 14:10
 */
public final class ClientProxy extends ServerProxy {
    public static final Minecraft mc = Minecraft.getMinecraft();

    final Thread clientThread = Thread.currentThread();

    @Override
    public boolean isMainThread(boolean atClientIfClient) {
        if (atClientIfClient)
            return Thread.currentThread() == clientThread;
        else
            return Thread.currentThread() == serverThread;
    }

    @Override
    public void runAtMainThread(boolean atClientIfClient, Runnable run) {
        boolean isAlreadyCurrentThread = isMainThread(atClientIfClient);
        if (isAlreadyCurrentThread)
            run.run();
        else if (atClientIfClient)
            mc.addScheduledTask(run);
        else
            PlayerUtil.getMinecraftServer().addScheduledTask(run);
    }

    @Override
    void setServerThread(Thread serverThread) {
        this.serverThread = serverThread;
    }

    @Override
    void preInit() {
        super.preInit();

        TextureHelper.preInit();

        ClientEvent.init();
    }

    @Override
    void init() {
        super.init();

        RenderManager man = mc.getRenderManager();

        man.entityRenderMap.put(EntityLightningBoltMI.class, new RenderLightningBoltMI(man));
        man.entityRenderMap.put(EntityTNTPrimed.class, new RenderTNTMy(man));

        MirrorSubSystem.initClient();

        KeyRegister.init();
    }

    @Override
    void postInit() {
        TextureHelper.postInit();

        ClientCommandHandler.instance.registerCommand(new MasterCommand("il_client", 0)
                .register(MySubs.DUMP_GL_INFO)
                .register(MySubs.RELOAD_TEXTURE)
                .register(MySubs.PACKAGE_SIMULATOR)
                .register(MySubs.GC)
                .register(new CommandPixelPainting())
        );
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        ImpLib.HOOK.triggerOnce(Hook.MODEL_REGISTER);

        for (ModelInfo info : Registry.getModels()) {
            if (info instanceof ItemModelInfo) {
                ItemModelInfo info1 = (ItemModelInfo) info;
                //ImpLib.logger().info("Registering model for " + info1.item + '@' + info1.meta + " to " + info1.model);
                ModelLoader.setCustomModelResourceLocation(info1.item, info1.meta, info1.model);
            } else {
                BlockModelInfo info1 = (BlockModelInfo) info;

                if (info1.model != null) {
                    //ImpLib.logger().info("Registering model for Block " + info1.block + " to " + info1.model);
                    ModelLoader.setCustomStateMapper(info1.block, new SingleTexture(info1.model));
                    final Item item = info1.item();
                    if (item != null) {
                        //ImpLib.logger().info("Registering model for " + item + "@0 to " + info1.model);
                        ModelLoader.setCustomModelResourceLocation(item, 0, info1.model);
                    }
                }
            }
        }

        registerGenModel();
    }

    private static BlockStateBuilder blockMergedModel;
    private static BlockStateBuilder itemMergedModel;
    private static BlockStateBuilder fluidMergedModel;

    public static BlockStateBuilder getBlockMergedModel() {
        return blockMergedModel;
    }

    public static BlockStateBuilder getItemMergedModel() {
        return itemMergedModel;
    }

    public static BlockStateBuilder getFluidMergedModel() {
        return fluidMergedModel;
    }

    static {
        try {
            itemMergedModel = new BlockStateBuilder(new String(IOUtil.read(ClientProxy.class, "assets/" + ImpLib.MODID + "/blockstates/items.json"), StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            itemMergedModel = new BlockStateBuilder(true);
        }
        try {
            blockMergedModel = new BlockStateBuilder(new String(IOUtil.read(ClientProxy.class, "assets/" + ImpLib.MODID + "/blockstates/blocks.json"), StandardCharsets.UTF_8), false);
        } catch (IOException e) {
            blockMergedModel = new BlockStateBuilder(false);
        }
        fluidMergedModel = new BlockStateBuilder(false).setDefaultModel("forge:fluid");
    }

    private static void registerGenModel() {
        String fluidPath = "assets/" + ImpLib.MODID + "/blockstates/generated/fluids.json";
        GeneratedModelRepo.register(fluidPath, fluidMergedModel.build());
        fluidMergedModel = null;

        String itemPath = "assets/" + ImpLib.MODID + "/blockstates/generated/items.json";
        GeneratedModelRepo.register(itemPath, itemMergedModel.build());
        itemMergedModel = null;

        String blockPath = "assets/" + ImpLib.MODID + "/blockstates/generated/blocks.json";
        GeneratedModelRepo.register(blockPath, blockMergedModel.build());
        blockMergedModel = null;
    }

    @SubscribeEvent
    public void blockColors(ColorHandlerEvent.Block event) {
        List<Block> COLOR_BLOCKS = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Block> entry : Registries.block().getEntries()) {
            if (entry.getValue() instanceof BlockColor) {
                COLOR_BLOCKS.add(entry.getValue());
            }
        }
        event.getBlockColors().registerBlockColorHandler(new Tinter(), COLOR_BLOCKS.toArray(new Block[COLOR_BLOCKS.size()]));
    }

    @SubscribeEvent
    public void itemColors(ColorHandlerEvent.Item event) {
        List<Item> COLOR_ITEMS = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Item> entry : Registries.item().getEntries()) {
            if (entry.getValue() instanceof ItemColor) {
                COLOR_ITEMS.add(entry.getValue());
            }
        }
        // 第二个参数代表“所有需要使用此 IItemColor 的物品”，是一个 var-arg Item。
        event.getItemColors().registerItemColorHandler(new Tinter(), COLOR_ITEMS.toArray(new Item[COLOR_ITEMS.size()]));
    }

    @Override
    public void registerFluidModel(String fluid, BlockFluidBase block) {
        String fluidId = ImpLib.MODID + ":generated/fluids";

        CMapping modelMap = new CMapping();
        modelMap.put("fluid", ForgeUtil.getCurrentModId() + ':' + fluid);

        fluidMergedModel.setVariantNonType(fluid, "custom", modelMap);

        final ModelResourceLocation path = new ModelResourceLocation(fluidId, fluid);

        final Item item = Item.getItemFromBlock(block);
        if (item != Items.AIR)
            ModelLoader.setCustomMeshDefinition(item, (stack) -> path);
        ModelLoader.setCustomStateMapper(block, new SingleTexture(path));
    }

    protected static class SingleTexture extends StateMapperBase {
        private final ModelResourceLocation path;

        public SingleTexture(ModelResourceLocation path) {
            this.path = path;
        }

        @Nonnull
        @Override
        protected ModelResourceLocation getModelResourceLocation(@Nonnull IBlockState state) {
            return path;
        }
    }
}
