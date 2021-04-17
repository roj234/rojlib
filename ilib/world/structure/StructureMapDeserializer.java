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
package ilib.world.structure;

import ilib.world.structure.cascading.SizedStructure;
import ilib.world.structure.cascading.StructureMap;
import ilib.world.structure.cascading.Structures;
import ilib.world.structure.cascading.api.IStructure;
import ilib.world.structure.cascading.api.StructureGroup;
import ilib.world.structure.schematic.SchematicLoader;
import net.minecraft.util.EnumFacing;
import roj.collect.MyHashMap;
import roj.config.ObjSerializer;
import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.config.data.CObject;
import roj.config.data.Type;
import roj.util.Helpers;

import java.io.File;
import java.util.EnumSet;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/20 1:38
 */
class StructureMapDeserializer implements ObjSerializer<StructureMap> {
    @Override
    public StructureMap deserialize(CObject<StructureMap> object) {
        String start = object.getString("start");
        int maxItr = object.getInteger("maxIteration");

        Map<String, IStructure> structureMap = new MyHashMap<>();
        for (Map.Entry<String, CEntry> ce : object.get("structures").asMap().entrySet()) {
            CMapping map1 = ce.getValue().asMap();
            IStructure structure = null;
            switch (map1.getString("type")) {
                case "FILE":
                    structure = new SizedStructure(SchematicLoader.INSTANCE.loadSchematic(new File(map1.getString("path"))));
                    break;
            }

            structureMap.put(ce.getKey(), structure);
        }

        Map<String, StructureGroup> groups = new MyHashMap<>();
        for (CEntry ce : object.get("entries").asList()) {
            StructureGroup group = parseGroup(structureMap, ce.asMap(), Helpers.cast(groups));
            groups.put(group.getName(), group);
        }
        return new StructureMap(start, groups.values());
    }

    private StructureGroup parseGroup(Map<String, IStructure> structureMap, CMapping map, Map<String, Structures> groups) {
        Structures.Builder builder = Structures.builder(map.getString("name"));
        for (CEntry ce : map.get("groups").asList()) {
            CMapping map1 = ce.asMap();
            builder.group(structureMap.get(map1.getString("structure")), map1.containsKey("frequency", Type.DOUBLE) ? map1.getDouble("frequency") : Double.NaN);
            for (CEntry ce1 : map1.get("entries").asList()) {
                parseEntry(builder, ce1.asMap(), groups);
            }
        }
        return builder.build();
    }

    private void parseEntry(Structures.Builder builder, CMapping map, Map<String, Structures> groups) {
        double frequency = map.containsKey("frequency", Type.DOUBLE) ? map.getDouble("frequency") : Double.NaN;
        String next = map.getString("next");
        int i = next.lastIndexOf('#');
        if (i != -1) {
            String k = next.substring(0, i);

            Structures group = groups.get(k);
            if (group == null)
                throw new IllegalArgumentException("Group " + k + " is not available due to upward reference");

            int v = Integer.parseInt(next.substring(i + 1));

            builder.inherit(group, v);
        } else {
            EnumSet<EnumFacing> facings = EnumSet.noneOf(EnumFacing.class);

            String s = map.getString("facing");
            for (i = 0; i < s.length(); i++) {
                switch (s.charAt(i)) {
                    case 'U':
                        facings.add(EnumFacing.UP);
                        break;
                    case 'D':
                        facings.add(EnumFacing.DOWN);
                        break;
                    case 'N':
                        facings.add(EnumFacing.NORTH);
                        break;
                    case 'S':
                        facings.add(EnumFacing.SOUTH);
                        break;
                    case 'W':
                        facings.add(EnumFacing.WEST);
                        break;
                    case 'E':
                        facings.add(EnumFacing.EAST);
                        break;
                }
            }
            builder.next(next, frequency, facings.toArray(new EnumFacing[facings.size()]));
        }
    }

    @Override
    public void serialize(CObject<StructureMap> base, StructureMap object) {
        throw new UnsupportedOperationException();
    }
}
