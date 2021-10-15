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
package ilib.asm.fasterforge;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.MyHashSet;

import net.minecraft.launchwrapper.Launch;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.relauncher.libraries.LibraryManager;
import net.minecraftforge.fml.relauncher.libraries.ModList;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/1/2 13:44
 */
@Nixim("net.minecraftforge.fml.relauncher.libraries.LibraryManager")
//!!AT ["net.minecraftforge.fml.relauncher.libraries.ModList", ["cache"], true]
public class NiximModFind extends LibraryManager {

    @Shadow("MOD_FILENAME_FILTER")
    private static FilenameFilter MOD_FILENAME_FILTER;
    @Shadow("FILE_NAME_SORTER_INSENSITVE")
    private static Comparator<File> FILE_NAME_SORTER_INSENSITVE;

    @Inject("gatherLegacyCanidates")
    @SuppressWarnings("unchecked")
    public static List<File> gatherLegacyCanidates(File mcDir) {
        MyHashSet<File> list = new MyHashSet<>();
        Map<String, String> args = (Map) Launch.blackboard.get("forgeLaunchArgs");
        String extraMods = args.get("--mods");
        String[] var4;
        int var5;
        int var6;
        String mod;
        File file;
        if (extraMods != null) {
            FMLLog.log.info("从指令中找mod:");
            var4 = extraMods.split(",");
            var5 = var4.length;

            for (var6 = 0; var6 < var5; ++var6) {
                mod = var4[var6];
                file = new File(mcDir, mod);
                if (!file.exists()) {
                    FMLLog.log.info("  无法找到 {} 的文件 {}", mod, file.getAbsolutePath());
                } else if (!list.contains(file)) {
                    FMLLog.log.debug("  已加载 {} ({})", mod, file.getAbsolutePath());
                    list.add(file);
                } else {
                    FMLLog.log.debug("  重复的mod {} ({})", mod, file.getAbsolutePath());
                }
            }
        }

        var4 = new String[]{"mods", "mods" + File.separatorChar + "1.12.2"};
        var5 = var4.length;

        for (var6 = 0; var6 < var5; ++var6) {
            mod = var4[var6];
            file = new File(mcDir, mod);
            if (file.isDirectory() && file.exists()) {
                FMLLog.log.info("正在 {} 中寻找mod", file.getAbsolutePath());
                for (File f : file.listFiles(MOD_FILENAME_FILTER)) {
                    if (!list.contains(f)) {
                        FMLLog.log.debug("  找到可能的mod {}", f.getName());
                        list.add(f);
                    }
                }
            }
        }

        List<File> files = new ArrayList<>(list);

        ModList memory = ModList.cache.get("MEMORY");
        if (!ENABLE_AUTO_MOD_MOVEMENT && memory != null && memory.getRepository() != null) {
            memory.getRepository().filterLegacy(files);
        }

        files.sort(FILE_NAME_SORTER_INSENSITVE);
        return files;
    }
}
