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
package roj.dev;

import roj.config.ParseException;
import roj.config.XMLParser;
import roj.config.data.AbstXML;
import roj.config.data.XElement;
import roj.config.data.XHeader;
import roj.io.IOUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Automatic add/remove IDEA source roots
 *
 * @author solo6975
 * @since 2021/7/23 10:57
 */
public final class IDEAHelper {
    public static void config(File imlPath, String projName, boolean remove) throws IOException, ParseException {
        String basePath = "file://$MODULE_DIR$/projects/" + projName + "/";

        XHeader header = XMLParser.parse(IOUtil.readUTF(new FileInputStream(imlPath)));
        XElement content = header.getXS("module.component[name=\"NewModuleRootManager\"].content").get(0).asElement();
        List<AbstXML> sourceUrls = content.childElements();
        for (int i = 0; i < sourceUrls.size(); i++) {
            XElement element = sourceUrls.get(i).asElement();
            if(element.getAttribute("url").asString().startsWith(basePath)) {
                if(remove)
                    sourceUrls.remove(i--);
                else
                    return;
            }
        }

        if(!remove) {
            XElement el = new XElement("sourceFolder");
            el.put("isTestSource", "false");
            el.put("url", basePath + "java");
            content.append(el);
            el = new XElement("sourceFolder");
            el.put("type", "java-resource");
            el.put("url", basePath + "resources");
            content.append(el);
        }

        try (FileOutputStream fos = new FileOutputStream(imlPath)) {
            ByteList.encodeUTF(header.toXML(new StringBuilder())).writeToStream(fos);
        }
    }
}
