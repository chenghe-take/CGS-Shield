import java.net.URISyntaxException;
import java.nio.file.*;

/** Locate the project when IntelliJ runs from the root, src, or compiled classes. */
public final class CGSPaths {
    private CGSPaths() { }

    public static Path resolve(String value) {
        Path path = Paths.get(value);
        if (path.isAbsolute()) return path.normalize();
        Path root = findRoot(Paths.get("").toAbsolutePath());
        if (root == null) {
            try {
                root = findRoot(Paths.get(CGSPaths.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()));
            } catch (URISyntaxException | SecurityException ex) {
                throw new IllegalStateException("Cannot locate the CGS-Shield project", ex);
            }
        }
        return (root == null ? Paths.get("").toAbsolutePath() : root).resolve(path).normalize();
    }

    private static Path findRoot(Path path) {
        for (Path current = path; current != null; current = current.getParent())
            if (Files.isDirectory(current.resolve("Data"))
                    && Files.isRegularFile(current.resolve("src/MainTestOriginal.java"))) return current;
        return null;
    }
}
