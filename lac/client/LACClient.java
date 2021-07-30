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
package lac.client;

import lac.client.util.MyBase64;
import lac.common.pkt.PktLogin;
import lac.server.note.DefaultObfuscatePolicy;
import lac.server.note.Obfuscate;
import lac.server.note.RandomInject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * LAC Mod Client-side entry
 * @author Roj234
 * @since 2021/7/9 2:02
 */
//@Mod(modid = "cs5", name = "CustomStuff 5", version = "5.0.3")
@DefaultObfuscatePolicy(onlyHaveStatic = false)
@RandomInject(ids = "b64char_rnd", value = 888888L, type = 'J')
public class LACClient {
    public static List<String> CLASS_CHECKS = Collections.emptyList();

    static {
        MyBase64.TABLEENC = new byte[] {
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
                'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
                'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/', '='
        };
        MyBase64.shuffle(MyBase64.TABLEENC, new Random(888888L));
    }

    public LACClient() {
        PktLogin.register();
    }

    @Mod.EventHandler
    @Obfuscate
    public void onPreInit(FMLPreInitializationEvent e) {

    }

    @Mod.EventHandler
    @Obfuscate
    public void onInit(FMLInitializationEvent e) {

    }

    @Mod.EventHandler
    @Obfuscate
    public void onPostInit(FMLPostInitializationEvent e) {

    }
}
