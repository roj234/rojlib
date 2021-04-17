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

package ilib.misc;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/22 20:10
 */
public abstract class FileNBTStorage {
    public final File file;

    public FileNBTStorage(File file) {
        this.file = file;
        this.load();
    }

    public abstract void loadFrom(NBTTagCompound list);

    public abstract NBTTagCompound writeTo();

    public void save() {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            CompressedStreamTools.writeCompressed(writeTo(), fos);
        } catch (IOException e) {
            throw new RuntimeException("Data save failed", e);
        }
    }

    public void load() {
        if (!file.exists()) {
            this.loadFrom(new NBTTagCompound());
            return;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            this.loadFrom(CompressedStreamTools.readCompressed(fis));
        } catch (IOException e) {
            throw new RuntimeException("Data load failed", e);
        }
    }
}