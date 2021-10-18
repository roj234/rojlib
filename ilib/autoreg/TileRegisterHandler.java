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
package ilib.autoreg;

import ilib.Config;
import ilib.Registry;
import ilib.asm.Loader;
import ilib.client.register.ItemModelInfo;
import ilib.item.ItemSelectTool;
import ilib.util.ForgeUtil;
import net.minecraft.item.Item;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.discovery.ASMDataTable.ASMData;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public class TileRegisterHandler {
    @SuppressWarnings("unchecked")
    public static void register() {
        for (ASMData c : Loader.ASMTable.getAll("ilib.autoreg.TileRegister")) {
            String cn = c.getClassName();
            try {
                Class<?> clazz = Class.forName(cn, false, Launch.classLoader);
                String str = (String) c.getAnnotationInfo().get("value");
                if (str.equals("")) {
                    str = clazz.getSimpleName().toLowerCase();
                    if (str.startsWith("tileentity"))
                        str = str.substring(10);
                    else if (str.startsWith("tile"))
                        str = str.substring(4);
                    str = ForgeUtil.getCurrentModId() + ':' + str;
                }
                TileEntity.register(str, (Class<? extends TileEntity>) clazz);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(cn + " thrown an error during initialize process. This is NOT an ImpLib bug.", e);
            }
        }

        if (!Config.registerItem) return;
        Item item = new ItemSelectTool();
        Registry.items().add(item.setRegistryName("ilib", "select_tool").setTranslationKey("ilib.select_tool"));
        Registry.model(new ItemModelInfo(item, true));
    }

//    private static AttrCode genClInit(ConstantData data) {
//        int i = data.getMethodByName("<clinit>");
//        MethodSimple clinit;
//        if (i >= 0) {
//            clinit = data.methods.get(i);
//        } else {
//            clinit = new MethodSimple(new FlagList(AccessFlag.PUBLIC | AccessFlag.STATIC),
//                                      data.cp.getUtf("<clinit>"), data.cp.getUtf("()V"));
//            data.methods.add(clinit);
//        }
//        Attribute code = clinit.attrByName("Code");
//        if (!(code instanceof AttrCode)) {
//            AttrCode c;
//            if (code == null) {
//                c = new AttrCode(clinit);
//                c.instructions.add(NPInsnNode.of(Opcodes.RETURN));
//            } else {
//                c = new AttrCode(clinit, Parser.reader(code), data.cp);
//                c.instructions.remove(c.instructions.size() - 1); // END_MARk
//            }
//            clinit.attributes.putByName(code = c);
//        }
//        return (AttrCode) code;
//    }
}