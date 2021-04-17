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

package ilib.api.registry;

import ilib.ImpLib;
import ilib.world.structure.schematic.Schematic;
import ilib.world.structure.schematic.SchematicLoader;
import roj.io.FileUtil;

import java.io.File;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/2 23:45
 */
public final class Structures extends Indexable.Impl {
    private static final RegistrySimple<Structures> WRAPPER = new RegistrySimple<>(Structures[]::new);
    private static int index = 0;

    public static void load(File f) {
        List<File> files = FileUtil.findAllFiles(f);
        for (File file : files) {
            Schematic schematic = SchematicLoader.INSTANCE.loadSchematic(file);
            if (schematic != null) {
                new Structures(file.getName(), schematic);
            } else {
                ImpLib.logger().warn(file.getAbsolutePath() + " is not a schematic file!");
            }
        }
    }

    public Structures(String name, Schematic file) {
        super(name, index++);
        this.structure = file;

        WRAPPER.appendValue(this);
    }

    public static Structures[] values() {
        return WRAPPER.values();
    }

    public static Structures byId(int id) {
        return WRAPPER.byId(id);
    }

    private final Schematic structure;

    public Schematic getSchematic() {
        return structure;
    }
}