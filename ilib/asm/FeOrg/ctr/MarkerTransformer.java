package ilib.asm.FeOrg.ctr;

import ilib.api.ContextClassTransformer;
import roj.asm.tree.ConstantData;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.text.LineReader;
import roj.text.TextUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MarkerTransformer implements ContextClassTransformer {
	private final MyHashMap<String, List<String>> markers;

	public MarkerTransformer() throws IOException {
		this("fml_marker.cfg");
	}

	protected MarkerTransformer(String rulesFile) throws IOException {
		this.markers = new MyHashMap<>();
		readMapFile(rulesFile);
	}

	private void readMapFile(String rulesFile) throws IOException {
		String rulesResource;
		File file = new File(rulesFile);
		if (!file.exists()) {
			rulesResource = IOUtil.readUTF(MarkerTransformer.class, rulesFile);
		} else {
			rulesResource = IOUtil.readUTF(new FileInputStream(file));
		}
		ArrayList<String> tmp = new ArrayList<>();
		for (String input : new LineReader(rulesResource)) {
			TextUtil.split(tmp, input, '#', 2);
			if (tmp.size() == 0) continue;
			String str = tmp.get(0);
			tmp.clear();
			TextUtil.split(tmp, str, ' ');

			if (tmp.size() != 2) throw new RuntimeException("Invalid config file line " + input);
			String name = tmp.get(0).trim();
			String val = tmp.get(1);
			tmp.clear();
			TextUtil.split(tmp, val, ',');
			for (int i = 0; i < tmp.size(); i++) {
				tmp.set(i, tmp.get(i).trim());
			}
			markers.put(name, new ArrayList<>(tmp));
		}
	}

	@Override
	public void transform(String transformedName, Context context) {
		List<String> itfs = this.markers.remove(transformedName);
		if (itfs == null) return;
		ConstantData data = context.getData();
		for (int i = 0; i < itfs.size(); i++) {
			data.interfaces.add(data.cp.getClazz(itfs.get(i)));
		}
	}
}
