package roj.plugins.obfuscator.naming;

import roj.collect.BitSet;
import roj.collect.ArrayList;
import roj.text.CharList;
import roj.text.TextReader;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * @author Roj233
 * @since 2021/7/18 19:29
 */
public final class StringList extends SimpleNamer {
	public List<String> names = new ArrayList<>();

	public StringList() {}

	public StringList(File file) throws IOException {
		try (TextReader sr = TextReader.auto(file)) {
			while (true) {
				String line = sr.readLine();
				if (line == null) break;
				if (!isValid(line)) {
					System.out.println("Not a valid class name: " + line);
				} else {
					names.add(line);
				}
			}
		}
	}

	private static final BitSet INVALID = BitSet.from(";[%./");
	static boolean isValid(String s) {
		for (int i = 0; i < s.length(); i++)
			if (INVALID.contains(s.charAt(i)))
				return false;
		return true;
	}

	@Override
	public boolean generateName(Random rand, CharList sb, int target) {
		sb.append(names.get(rand.nextInt(names.size())));
		return true;
	}
}