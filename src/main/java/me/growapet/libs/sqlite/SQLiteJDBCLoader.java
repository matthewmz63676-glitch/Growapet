/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.LinkedList;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;
import me.growapet.libs.sqlite.FileException;
import me.growapet.libs.sqlite.NativeLibraryNotFoundException;
import me.growapet.libs.sqlite.util.LibraryLoaderUtil;
import me.growapet.libs.sqlite.util.Logger;
import me.growapet.libs.sqlite.util.LoggerFactory;
import me.growapet.libs.sqlite.util.OSInfo;
import me.growapet.libs.sqlite.util.StringUtils;

public class SQLiteJDBCLoader {
    private static final Logger logger = LoggerFactory.getLogger(SQLiteJDBCLoader.class);
    private static final String LOCK_EXT = ".lck";
    private static boolean extracted = false;

    public static synchronized boolean initialize() throws Exception {
        if (!extracted) {
            SQLiteJDBCLoader.cleanup();
        }
        SQLiteJDBCLoader.loadSQLiteNativeLibrary();
        return extracted;
    }

    private static File getTempDir() {
        return new File(System.getProperty("me.growapet.libs.sqlite.tmpdir", System.getProperty("java.io.tmpdir")));
    }

    static void cleanup() {
        String searchPattern = "sqlite-" + SQLiteJDBCLoader.getVersion();
        try (Stream<Path> dirList = Files.list(SQLiteJDBCLoader.getTempDir().toPath());){
            dirList.filter(path -> !path.getFileName().toString().endsWith(LOCK_EXT) && path.getFileName().toString().startsWith(searchPattern)).forEach(nativeLib -> {
                Path lckFile = Paths.get(nativeLib + LOCK_EXT, new String[0]);
                if (Files.notExists(lckFile, new LinkOption[0])) {
                    try {
                        Files.delete(nativeLib);
                    }
                    catch (Exception e) {
                        logger.error("Failed to delete old native lib", e);
                    }
                }
            });
        }
        catch (IOException e) {
            logger.error("Failed to open directory", e);
        }
    }

    public static boolean isNativeMode() throws Exception {
        SQLiteJDBCLoader.initialize();
        return extracted;
    }

    static String md5sum(InputStream input) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(input);){
            MessageDigest digest = MessageDigest.getInstance("MD5");
            DigestInputStream digestInputStream = new DigestInputStream(in, digest);
            while (digestInputStream.read() >= 0) {
            }
            ByteArrayOutputStream md5out = new ByteArrayOutputStream();
            md5out.write(digest.digest());
            String string = md5out.toString();
            return string;
        }
    }

    private static boolean contentsEquals(InputStream in1, InputStream in2) throws IOException {
        int ch2;
        if (!(in1 instanceof BufferedInputStream)) {
            in1 = new BufferedInputStream(in1);
        }
        if (!(in2 instanceof BufferedInputStream)) {
            in2 = new BufferedInputStream(in2);
        }
        int ch = in1.read();
        while (ch != -1) {
            ch2 = in2.read();
            if (ch != ch2) {
                return false;
            }
            ch = in1.read();
        }
        ch2 = in2.read();
        return ch2 == -1;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static boolean extractAndLoadLibraryFile(String libFolderForCurrentOS, String libraryFileName, String targetFolder) throws FileException {
        String nativeLibraryFilePath = libFolderForCurrentOS + "/" + libraryFileName;
        String uuid = UUID.randomUUID().toString();
        String extractedLibFileName = String.format("sqlite-%s-%s-%s", SQLiteJDBCLoader.getVersion(), uuid, libraryFileName);
        String extractedLckFileName = extractedLibFileName + LOCK_EXT;
        Path extractedLibFile = Paths.get(targetFolder, extractedLibFileName);
        Path extractedLckFile = Paths.get(targetFolder, extractedLckFileName);
        try {
            try (InputStream reader = SQLiteJDBCLoader.getResourceAsStream(nativeLibraryFilePath);){
                if (Files.notExists(extractedLckFile, new LinkOption[0])) {
                    Files.createFile(extractedLckFile, new FileAttribute[0]);
                }
                Files.copy(reader, extractedLibFile, StandardCopyOption.REPLACE_EXISTING);
            }
            finally {
                extractedLibFile.toFile().deleteOnExit();
                extractedLckFile.toFile().deleteOnExit();
            }
            extractedLibFile.toFile().setReadable(true);
            extractedLibFile.toFile().setWritable(true, true);
            extractedLibFile.toFile().setExecutable(true);
            try (InputStream nativeIn = SQLiteJDBCLoader.getResourceAsStream(nativeLibraryFilePath);
                 InputStream extractedLibIn = Files.newInputStream(extractedLibFile, new OpenOption[0]);){
                if (!SQLiteJDBCLoader.contentsEquals(nativeIn, extractedLibIn)) {
                    throw new FileException(String.format("Failed to write a native library file at %s", extractedLibFile));
                }
            }
            return SQLiteJDBCLoader.loadNativeLibrary(targetFolder, extractedLibFileName);
        }
        catch (IOException e) {
            logger.error("Unexpected IOException", e);
            return false;
        }
    }

    private static InputStream getResourceAsStream(String name) {
        String resolvedName = name.substring(1);
        ClassLoader cl = SQLiteJDBCLoader.class.getClassLoader();
        URL url = cl.getResource(resolvedName);
        if (url == null) {
            return null;
        }
        try {
            URLConnection connection = url.openConnection();
            connection.setUseCaches(false);
            return connection.getInputStream();
        }
        catch (IOException e) {
            logger.error("Could not connect", e);
            return null;
        }
    }

    private static boolean loadNativeLibrary(String path, String name) {
        File libPath = new File(path, name);
        if (libPath.exists()) {
            try {
                System.load(new File(path, name).getAbsolutePath());
                return true;
            }
            catch (UnsatisfiedLinkError e) {
                logger.error("Failed to load native library: {}. osinfo: {}", name, OSInfo.getNativeLibFolderPathForCurrentOS(), e);
                return false;
            }
        }
        return false;
    }

    private static boolean loadNativeLibraryJdk() {
        try {
            System.loadLibrary("sqlitejdbc");
            return true;
        }
        catch (UnsatisfiedLinkError e) {
            logger.error("Failed to load native library through System.loadLibrary", e);
            return false;
        }
    }

    private static void loadSQLiteNativeLibrary() throws Exception {
        boolean hasNativeLib;
        if (extracted) {
            return;
        }
        LinkedList<String> triedPaths = new LinkedList<String>();
        String sqliteNativeLibraryPath = System.getProperty("me.growapet.libs.sqlite.lib.path");
        String sqliteNativeLibraryName = System.getProperty("me.growapet.libs.sqlite.lib.name");
        if (sqliteNativeLibraryName == null) {
            sqliteNativeLibraryName = LibraryLoaderUtil.getNativeLibName();
        }
        if (sqliteNativeLibraryPath != null) {
            if (SQLiteJDBCLoader.loadNativeLibrary(sqliteNativeLibraryPath, sqliteNativeLibraryName)) {
                extracted = true;
                return;
            }
            triedPaths.add(sqliteNativeLibraryPath);
        }
        if (hasNativeLib = LibraryLoaderUtil.hasNativeLib(sqliteNativeLibraryPath = LibraryLoaderUtil.getNativeLibResourcePath(), sqliteNativeLibraryName)) {
            String tempFolder = SQLiteJDBCLoader.getTempDir().getAbsolutePath();
            if (SQLiteJDBCLoader.extractAndLoadLibraryFile(sqliteNativeLibraryPath, sqliteNativeLibraryName, tempFolder)) {
                extracted = true;
                return;
            }
            triedPaths.add(sqliteNativeLibraryPath);
        }
        String javaLibraryPath = System.getProperty("java.library.path", "");
        for (String ldPath : javaLibraryPath.split(File.pathSeparator)) {
            if (ldPath.isEmpty()) continue;
            if (SQLiteJDBCLoader.loadNativeLibrary(ldPath, sqliteNativeLibraryName)) {
                extracted = true;
                return;
            }
            triedPaths.add(ldPath);
        }
        if (SQLiteJDBCLoader.loadNativeLibraryJdk()) {
            extracted = true;
            return;
        }
        extracted = false;
        throw new NativeLibraryNotFoundException(String.format("No native library found for os.name=%s, os.arch=%s, paths=[%s]", OSInfo.getOSName(), OSInfo.getArchName(), StringUtils.join(triedPaths, File.pathSeparator)));
    }

    private static void getNativeLibraryFolderForTheCurrentOS() {
        String osName = OSInfo.getOSName();
        String archName = OSInfo.getArchName();
    }

    public static int getMajorVersion() {
        String[] c = SQLiteJDBCLoader.getVersion().split("\\.");
        return c.length > 0 ? Integer.parseInt(c[0]) : 1;
    }

    public static int getMinorVersion() {
        String[] c = SQLiteJDBCLoader.getVersion().split("\\.");
        return c.length > 1 ? Integer.parseInt(c[1]) : 0;
    }

    public static String getVersion() {
        return VersionHolder.VERSION;
    }

    public static final class VersionHolder {
        private static final String VERSION;

        static {
            URL versionFile = VersionHolder.class.getResource("/META-INF/maven/org.xerial/sqlite-jdbc/pom.properties");
            if (versionFile == null) {
                versionFile = VersionHolder.class.getResource("/META-INF/maven/org.xerial/sqlite-jdbc/VERSION");
            }
            String version = "unknown";
            try {
                if (versionFile != null) {
                    Properties versionData = new Properties();
                    versionData.load(versionFile.openStream());
                    version = versionData.getProperty("version", version);
                    version = version.trim().replaceAll("[^0-9\\.]", "");
                }
            }
            catch (IOException e) {
                LoggerFactory.getLogger(VersionHolder.class).error("Could not read version from file: {}", versionFile, e);
            }
            VERSION = version;
        }
    }
}

