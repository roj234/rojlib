import *

public class Test {
	public static void main(String[] args) {
		Files.walkFileTree(Path.of("F:\\"), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (attrs.size() < 1024) {
					File t = file.toFile();
					var otherSide = new File(t.getAbsolutePath().replace("F:", "E:"));
					if (otherSide.exists()) {
						BasicFileAttributeView view = Files.getFileAttributeView(otherSide.toPath(), BasicFileAttributeView.class);
						try {
							view.setTimes(attrs.lastModifiedTime(), attrs.lastAccessTime(), attrs.creationTime());
						} catch (IOException e) {
							e.printStackTrace();
						}
						System.out.println("updating "+t);
					}
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
