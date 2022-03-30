/*
 * This file is a part of MoreItems
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
package lac.injector.misc;

import lac.client.AccessHelper;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import roj.util.ByteList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Classes load detector
 *
 * @author Roj233
 * @since 2021/10/16 12:50
 */
//@Mod(modid = "lac_helper", name = "LaoAntiCheat Helper", version = "1.0.0")
public class LACDetector implements Runnable {
    @Mod.EventHandler
    public void onLoadComplete(FMLLoadCompleteEvent e) throws IOException {
        work();
        Runtime.getRuntime().addShutdownHook(new Thread(this));
    }

    private static void work() throws IOException {
        List<Class<?>> classes = ((AccessHelper) Launch.classLoader).getRaw(null);
        try (FileOutputStream fos = new FileOutputStream("lac-sorted-classes.txt")) {
            for (int i = 0; i < classes.size(); i++) {
                ByteList.encodeUTF(classes.get(i).getName()).writeToStream(fos);
            }
            fos.write('\n');
        }

        List<ModContainer> modList = Loader.instance().getActiveModList();
        try (FileOutputStream fos = new FileOutputStream("lac-mods.txt")) {
            for (int i = 0; i < modList.size(); i++) {
                ModContainer mod = modList.get(i);
                ByteList.encodeUTF(mod.getModId()).writeToStream(fos);
                fos.write('|');
                ByteList.encodeUTF(mod.getVersion()).writeToStream(fos);
            }
            fos.write('\n');
        }
    }

    @Override
    public void run() {
        try {
            work();
        } catch (IOException ignored) {}
    }
}
