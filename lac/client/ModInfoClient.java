package lac.client;

import lac.server.note.DefaultObfuscatePolicy;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Mod数据保存类
 *
 * @author Roj233
 * @since 2021/7/9 0:16
 */
@DefaultObfuscatePolicy(onlyHaveStatic = true)
public class ModInfoClient {
    public static List<File> gatherFileInFolder() {
        File mods = new File(new String(new char[]{'m', 'o','d','s'}));
        return null;
    }

    public static List<File> findAllFiles(File file) {
        return findAllFiles(file, new ArrayList<>());
    }

    private static List<File> findAllFiles(File file, List<File> files) {
        File[] files1 = file.listFiles();
        if (files1 != null) {
            for (File file1 : files1) {
                if (file1.isDirectory()) {
                    findAllFiles(file1, files);
                } else {
                    files.add(file1);
                }
            }
        }
        return files;
    }

    public static List<File> findAllFiles2(File file) {
        return findAllFiles2(file, new LinkedList<>());
    }

    private static List<File> findAllFiles2(File file, List<File> files) {
        File[] files1 = file.listFiles();
        if (files1 != null) {
            for (File file1 : files1) {
                if (file1.isDirectory()) {
                    findAllFiles(file1, files);
                } else {
                    files.add(file1);
                }
            }
        }
        return files;
    }
}
