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
package lac.injector.patch;

import com.google.common.collect.Maps;
import lac.client.AccessHelper;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * @author Roj233
 * @since 2021/10/18 3:15
 */
abstract class AccessHelperFake extends ClassLoader implements AccessHelper {
    // 方法的伪装变体，除了这些调用static的，还有内联的，为了体积，会少一点
    public List<Class<?>> F_S_0(ClassLoader loader) {
        return new ArrayList<>();
    }
    public List<Class<?>> F_S_1(ClassLoader loader) {
        return new LinkedList<>();
    }
    public List<Class<?>> F_S_2(ClassLoader loader) {
        return INJ_FS_0(this);
    }
    public List<Class<?>> F_S_3(ClassLoader loader) {
        return INJ_FS_1(loader);
    }
    public List<Class<?>> F_S_4(ClassLoader loader) {
        return INJ_FS_2(null);
    }
    public List<Class<?>> F_S_5(ClassLoader loader) {
        return INJ_FS_3(null);
    }
    public List<Class<?>> F_S_6(ClassLoader loader) {
        return INJ_FS_4(loader.hashCode(), loader instanceof URLClassLoader, loader.getClass().getName());
    }
    public List<Class<?>> F_S_7(ClassLoader loader) {
        return INJ_FS_5();
    }

    public Vector<Class<?>> F_R_0(ClassLoader loader) {
        return new Vector<>();
    }
    public Vector<Class<?>> F_R_1(ClassLoader loader) {
        return INJ_FS_1(loader);
    }
    public Vector<Class<?>> F_R_2(ClassLoader loader) {
        return (Vector<Class<?>>) INJ_FS_3(loader);
    }
    public Vector<Class<?>> F_R_3(ClassLoader loader) {
        return INJ_FR_0(loader);
    }
    public Vector<Class<?>> F_R_4(ClassLoader loader) {
        return INJ_FR_1(loader);
    }
    public Vector<Class<?>> F_R_5(ClassLoader loader) {
        return INJ_FR_2();
    }

    static List<Class<?>> INJ_FS_0(ClassLoader loader) {
        return new ArrayList<>(Maps.<String, Class<?>>newHashMap().values());
    }

    static Vector<Class<?>> INJ_FS_1(ClassLoader loader) {
        Vector<Class<?>> raw = ((AccessHelper) loader).getRaw(Object.class.getClassLoader());
        List<Class<?>> list = new ArrayList<>(raw);
        list.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
        return (Vector<Class<?>>) list;
    }

    static List<Class<?>> INJ_FS_2(ClassLoader loader) {
        List<Class<?>> list = new ArrayList<>();
        return list;
    }

    static List<Class<?>> INJ_FS_3(Object loader) {
        List<Class<?>> list = new ArrayList<>();
        return list;
    }

    static List<Class<?>> INJ_FS_4(int a, boolean b, String c) {
        List<Class<?>> list = new ArrayList<>();
        return list;
    }

    static List<Class<?>> INJ_FS_5() {
        List<Class<?>> list = new ArrayList<>();
        return list;
    }

    static Vector<Class<?>> INJ_FR_0(ClassLoader loader) {
        return null;
    }

    static Vector<Class<?>> INJ_FR_1(ClassLoader loader) {
        return null;
    }

    static Vector<Class<?>> INJ_FR_2() {
        return null;
    }
}
