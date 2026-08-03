/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite.util;

import me.growapet.libs.sqlite.SQLiteJDBCLoader;
import me.growapet.libs.sqlite.util.OSInfo;

public class LibraryLoaderUtil {
    public static final String NATIVE_LIB_BASE_NAME = "sqlitejdbc";

    public static String getNativeLibResourcePath() {
        String packagePath = SQLiteJDBCLoader.class.getPackage().getName().replace(".", "/");
        return String.format("/%s/native/%s", packagePath, OSInfo.getNativeLibFolderPathForCurrentOS());
    }

    public static String getNativeLibName() {
        return System.mapLibraryName(NATIVE_LIB_BASE_NAME);
    }

    public static boolean hasNativeLib(String path, String libraryName) {
        return SQLiteJDBCLoader.class.getResource(path + "/" + libraryName) != null;
    }
}

