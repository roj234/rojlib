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

package ilib.util;

import ilib.ImpLib;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.relauncher.FMLInjectionData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ForgeUtil {
    public static String getMcVersion() {
        return (String) FMLInjectionData.data()[4];
    }

    public static boolean isInModInitialisation() {
        return !hasReachedState(LoaderState.AVAILABLE);
    }

    public static boolean hasReachedState(LoaderState state) {
        return getLoader().hasReachedState(state);
    }

    @Nonnull
    public static Loader getLoader() {
        return Loader.instance();
    }

    @Nullable
    public static ModContainer findModById(String modId) {
        Iterator<ModContainer> i = getLoader().getActiveModList().iterator();

        ModContainer mc;
        do {
            if (!i.hasNext()) return null;
            mc = i.next();
        } while (!mc.getModId().equals(modId));

        return mc;
    }

    public static String getCurrentModId() {
        ModContainer container = getCurrentMod();
        return container == null ? ImpLib.MODID : container.getModId();
    }

    public static ModContainer getCurrentMod() {
        return Loader.instance().activeModContainer();
    }

    public static void appendChildModTo(String modid) {
        ForgeUtil.findModById(modid).getMetadata().childMods.add(getCurrentMod());
    }
}
