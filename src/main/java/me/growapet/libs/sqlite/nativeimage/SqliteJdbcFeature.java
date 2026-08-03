/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  me.growapet.libs.sqlite.nativeimage.SqliteJdbcFeature
 *  me.growapet.libs.sqlite.nativeimage.SqliteJdbcFeature$SqliteJdbcFeatureException
 *  org.graalvm.nativeimage.hosted.Feature
 *  org.graalvm.nativeimage.hosted.Feature$BeforeAnalysisAccess
 *  org.graalvm.nativeimage.hosted.Feature$DuringAnalysisAccess
 *  org.graalvm.nativeimage.hosted.RuntimeClassInitialization
 *  org.graalvm.nativeimage.hosted.RuntimeJNIAccess
 *  org.graalvm.nativeimage.hosted.RuntimeResourceAccess
 */
package me.growapet.libs.sqlite.nativeimage;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import me.growapet.libs.sqlite.BusyHandler;
import me.growapet.libs.sqlite.Collation;
import me.growapet.libs.sqlite.Function;
import me.growapet.libs.sqlite.ProgressHandler;
import me.growapet.libs.sqlite.SQLiteJDBCLoader;
import me.growapet.libs.sqlite.core.DB;
import me.growapet.libs.sqlite.core.NativeDB;
import me.growapet.libs.sqlite.jdbc3.JDBC3DatabaseMetaData;
import me.growapet.libs.sqlite.nativeimage.SqliteJdbcFeature;
import me.growapet.libs.sqlite.util.LibraryLoaderUtil;
import me.growapet.libs.sqlite.util.OSInfo;
import me.growapet.libs.sqlite.util.ProcessRunner;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

public class SqliteJdbcFeature
implements Feature {
    public void beforeAnalysis(Feature.BeforeAnalysisAccess a) {
        RuntimeClassInitialization.initializeAtBuildTime((Class[])new Class[]{SQLiteJDBCLoader.VersionHolder.class});
        RuntimeClassInitialization.initializeAtBuildTime((Class[])new Class[]{JDBC3DatabaseMetaData.class});
        RuntimeClassInitialization.initializeAtBuildTime((Class[])new Class[]{OSInfo.class});
        RuntimeClassInitialization.initializeAtBuildTime((Class[])new Class[]{ProcessRunner.class});
        RuntimeClassInitialization.initializeAtBuildTime((Class[])new Class[]{LibraryLoaderUtil.class});
        a.registerReachabilityHandler(arg_0 -> this.nativeDbReachable(arg_0), new Object[]{this.method(SQLiteJDBCLoader.class, "initialize", new Class[0])});
    }

    private void nativeDbReachable(Feature.DuringAnalysisAccess a) {
        this.handleLibraryResources();
        this.registerJNICalls();
    }

    private void handleLibraryResources() {
        String libraryName;
        String libraryPath = LibraryLoaderUtil.getNativeLibResourcePath();
        if (!LibraryLoaderUtil.hasNativeLib(libraryPath, libraryName = LibraryLoaderUtil.getNativeLibName())) {
            throw new SqliteJdbcFeatureException("Unable to locate the required native resources for native-image. Please contact the maintainers of sqlite-jdbc.", null);
        }
        String libraryResource = libraryPath + "/" + libraryName;
        String exportLocation = System.getProperty("me.growapet.libs.sqlite.lib.exportPath", "");
        if (exportLocation.isEmpty()) {
            RuntimeResourceAccess.addResource((Module)SQLiteJDBCLoader.class.getModule(), (String)libraryResource.substring(1));
        } else {
            Path targetPath = Paths.get(exportLocation, libraryName);
            try (InputStream in = SQLiteJDBCLoader.class.getResourceAsStream(libraryResource);){
                Files.createDirectories(targetPath.getParent(), new FileAttribute[0]);
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e) {
                throw new SqliteJdbcFeatureException((Throwable)e, null);
            }
        }
    }

    private void registerJNICalls() {
        RuntimeJNIAccess.register((Class[])new Class[]{NativeDB.class});
        RuntimeJNIAccess.register((Field[])this.fields(NativeDB.class, new String[]{"pointer", "busyHandler", "commitListener", "updateListener", "progressHandler"}));
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(DB.class, "onUpdate", new Class[]{Integer.TYPE, String.class, String.class, Long.TYPE})});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(DB.class, "onCommit", new Class[]{Boolean.TYPE})});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(NativeDB.class, "stringToUtf8ByteArray", new Class[]{String.class})});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(DB.class, "throwex", new Class[0])});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(DB.class, "throwex", new Class[]{Integer.TYPE})});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(NativeDB.class, "throwex", new Class[]{String.class})});
        RuntimeJNIAccess.register((Class[])new Class[]{Function.class});
        RuntimeJNIAccess.register((Field[])this.fields(Function.class, new String[]{"context", "value", "args"}));
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(Function.class, "xFunc", new Class[0])});
        RuntimeJNIAccess.register((Class[])new Class[]{Collation.class});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(Collation.class, "xCompare", new Class[]{String.class, String.class})});
        RuntimeJNIAccess.register((Class[])new Class[]{Function.Aggregate.class});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(Function.Aggregate.class, "xStep", new Class[0])});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(Function.Aggregate.class, "xFinal", new Class[0])});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(Function.Aggregate.class, "clone", new Class[0])});
        RuntimeJNIAccess.register((Class[])new Class[]{Function.Window.class});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(Function.Window.class, "xInverse", new Class[0])});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(Function.Window.class, "xValue", new Class[0])});
        RuntimeJNIAccess.register((Class[])new Class[]{DB.ProgressObserver.class});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(DB.ProgressObserver.class, "progress", new Class[]{Integer.TYPE, Integer.TYPE})});
        RuntimeJNIAccess.register((Class[])new Class[]{ProgressHandler.class});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(ProgressHandler.class, "progress", new Class[0])});
        RuntimeJNIAccess.register((Class[])new Class[]{BusyHandler.class});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(BusyHandler.class, "callback", new Class[]{Integer.TYPE})});
        RuntimeJNIAccess.register((Class[])new Class[]{Throwable.class});
        RuntimeJNIAccess.register((Executable[])new Executable[]{this.method(Throwable.class, "toString", new Class[0])});
        RuntimeJNIAccess.register((Class[])new Class[]{boolean[].class});
    }

    private Method method(Class<?> clazz, String methodName, Class<?> ... args) {
        try {
            return clazz.getDeclaredMethod(methodName, args);
        }
        catch (NoSuchMethodException e) {
            throw new SqliteJdbcFeatureException((Throwable)e, null);
        }
    }

    private Field[] fields(Class<?> clazz, String ... fieldNames) {
        try {
            Field[] fields = new Field[fieldNames.length];
            for (int i = 0; i < fieldNames.length; ++i) {
                fields[i] = clazz.getDeclaredField(fieldNames[i]);
            }
            return fields;
        }
        catch (NoSuchFieldException e) {
            throw new SqliteJdbcFeatureException((Throwable)e, null);
        }
    }
}

