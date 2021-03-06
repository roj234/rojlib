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
package ilib.client.model;

import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CDouble;
import roj.config.data.CInteger;
import roj.config.data.CList;
import roj.config.data.CMapping;

/**
 * @author Roj234
 * @since  2020/6/13 11:04
 */
public class BlockModelBuilder {
    public final CMapping jsonData;

    public BlockModelBuilder(String jsonData1) {
        try {
            jsonData = JSONParser.parse(jsonData1).asMap();
        } catch (ParseException e) {
            throw new RuntimeException("Illegal model data ", e);
        }
    }

    public BlockModelBuilder() {
        jsonData = new CMapping();
    }

    public String build() {
        return jsonData.toShortJSON();
    }

    public BlockModelBuilder parent(String parent) {
        jsonData.put("parent", parent);
        return this;
    }

    public BlockModelBuilder texture(String key, String val) {
        jsonData.getOrCreateMap("textures").put(key, val);
        return this;
    }

    public BlockModelBuilder element(Element element) {
        jsonData.getOrCreateList("elements").add(element.map);
        return this;
    }

    public static class Element {
        final CMapping map;
        private CMapping face;

        /**
         * "from": [ 0, 12, 1 ],
         * "to": [ 16, 16, 16 ],
         * "faces": {
         * "down": { "uv": [ 0, 7.5, 8.25, 16 ], "texture": "#texture" },
         * "up": { "uv": [ 0, 7.5, 8.25, 16 ], "texture": "#texture" },
         * "north": { "uv": [ 0, 0, 8, 2.5 ], "texture": "#texture" },
         * "south": { "uv": [ 8, 8, 16, 10 ], "texture": "#texture" },
         * "west": { "uv": [ 8.5, 0, 16, 2 ], "texture": "#texture" },
         * "east": { "uv": [ 8.5, 0, 16, 2 ], "texture": "#texture" }
         * }
         */

        public Element() {
            this.map = new CMapping();
        }

        public static Element builder() {
            return new Element();
        }

        /**
         * 0 - 16
         */
        public Element pos(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
            if (fromX > 16 || fromY > 16 || fromZ > 16 || toX > 16 || toY > 16 || toZ > 16 || fromX < 0 || fromY < 0 || fromZ < 0 || toX < 0 || toY < 0 || toZ < 0)
                throw new IllegalArgumentException();

            map.getOrCreateList("from").add(new CInteger(fromX)).add(new CInteger(fromY)).add(new CInteger(fromZ));
            map.getOrCreateList("to").add(new CInteger(toX)).add(new CInteger(toY)).add(new CInteger(toZ));
            return this;
        }

        public Element face(String name) {
            if (face != null) throw new IllegalArgumentException();
            face = map.getOrCreateMap("faces").getOrCreateMap(name);
            return this;
        }

        /**
         * ????????????????????????!
         */
        public Element uv(double x, double y, double w, double h) {
            CList list = face.getOrCreateList("uv");
            list.clear();
            list.add(new CDouble(x)).add(new CDouble(y)).add(new CDouble(w)).add(h);
            return this;
        }

        public Element tex(String id) {
            face.put("texture", id);
            return this;
        }

        public Element endFace() {
            if (face == null) throw new IllegalArgumentException();
            face = null;
            return this;
        }
    }
}
