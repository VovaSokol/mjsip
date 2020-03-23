package org.mjsip.custom;

import java.io.*;

/**
 * Class for loading libraries from resources.
 */

public class LibraryLoader {
    private static final String LIBRARY_PATH = "/lib";
    private static final String WIN_PLATFORM = "win";
    private static final String MAC_PLATFORM = "mac";
    private static final String LIN_PLATFORM = "linux";
    private static final String WIN_FILE_EXTENSION = "dll";
    private static final String MAC_FILE_EXTENSION = "dylib";
    private static final String LIN_FILE_EXTENSION = "so";

    /**
     * Detecting default library extension for all platforms
     * @return file extension
     */
    private static String getDefaultFileExtension(){
        if(System.getProperty("os.name").toLowerCase().contains(MAC_PLATFORM))
            return MAC_FILE_EXTENSION;
        else if(System.getProperty("os.name").toLowerCase().contains(LIN_PLATFORM))
            return LIN_FILE_EXTENSION;
        else if(System.getProperty("os.name").toLowerCase().contains(WIN_PLATFORM))
            return WIN_FILE_EXTENSION;
        return LIN_FILE_EXTENSION;
    }

    /**
     * Loading library from resources
     * @param libraryName full file name
     * @param fileExtension file extension if need or will be using default platform extension
     * @throws IOException throw exception if file not found
     */
    public static void loadLibrary(String libraryName, String... fileExtension) throws IOException{
        String extension;
        if (fileExtension == null || fileExtension.length == 0){
            extension = getDefaultFileExtension();
        } else {
            extension = fileExtension[0];
        }
        StringBuilder pathToLibrary = new StringBuilder(LIBRARY_PATH).append("/");
        pathToLibrary.append(System.getProperty("os.name").toLowerCase()).append("/");
        pathToLibrary.append(System.getProperty("os.arch").toLowerCase()).append("/");
        pathToLibrary.append(libraryName).append(".").append(extension);

        System.out.println("Library path: " + pathToLibrary.toString());
        InputStream in = LibraryLoader.class.getResourceAsStream(pathToLibrary.toString());
        byte[] buffer = new byte[1024];
        int read = -1;
        File temp = File.createTempFile(libraryName, "");
        FileOutputStream fos = new FileOutputStream(temp);

        while((read = in.read(buffer)) != -1) {
            fos.write(buffer, 0, read);
        }
        fos.close();
        in.close();

        System.load(temp.getAbsolutePath());
    }
}
