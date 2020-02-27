package org.mjsip.custom.opus;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Arrays;


public class NativeUtils {

    private NativeUtils() {
    }

    public static void loadLibraryFromJar(String jarpath, String[] libs) throws IOException {

        File libspath = File.createTempFile("libs", "");
        if (!libspath.delete()) {
            throw new IOException("Cannot clean " + libspath);
        }
        if (!libspath.exists()) {
            if (!libspath.mkdirs()) {
                throw new IOException("Cannot create directory " + libspath);
            }
        }

        libspath.deleteOnExit();

        try {
            addLibraryPath(libspath.getAbsolutePath());
        } catch (Exception e) {
            throw new IOException(e);
        }

        for (String lib : libs) {

            String libfile = "lib" + lib + ".so";
            String path = jarpath + "/" + libfile;

            if (!path.startsWith("/")) {
                throw new IllegalArgumentException("The path to be absolute (start with '/').");
            }

            File file = new File(libspath, libfile);
            file.createNewFile();
            file.deleteOnExit();

            byte[] buffer = new byte[1024];
            int readBytes;

            InputStream is = NativeUtils.class.getResourceAsStream(path);
            if (is == null) {
                throw new FileNotFoundException("File " + path + " was not found inside JAR.");
            }

            OutputStream os = new FileOutputStream(file);
            try {
                while ((readBytes = is.read(buffer)) != -1) {
                    os.write(buffer, 0, readBytes);
                }
            } finally {
                os.close();
                is.close();
            }
        }

        for (String lib : libs) {
            System.out.println("LOAD LIB: " + lib);
            System.loadLibrary(lib);
        }
    }

    public static void addLibraryPath(String pathToAdd) throws Exception {
        Field usrPathsField = ClassLoader.class.getDeclaredField("usr_paths");
        usrPathsField.setAccessible(true);

        // get array of paths
        final String[] paths = (String[]) usrPathsField.get(null);

        // check if the path to add is already present
        for (String path : paths) {
            if (path.equals(pathToAdd)) {
                return;
            }
        }

        // add the new path
        final String[] newPaths = Arrays.copyOf(paths, paths.length + 1);
        newPaths[newPaths.length - 1] = pathToAdd;
        usrPathsField.set(null, newPaths);
    }
}
