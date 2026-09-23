import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.FileInputStream;

public class Test3 {
    public static void main(String[] args) throws Exception {
        String cp = args[0];
        for (String path : cp.split(";")) {
            if (path.endsWith(".jar")) {
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(path))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.getName().endsWith("Media.class")) {
                            System.out.println("Found in " + path + ": " + entry.getName());
                        }
                    }
                }
            }
        }
    }
}
