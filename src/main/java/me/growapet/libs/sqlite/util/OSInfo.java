/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.stream.Stream;
import me.growapet.libs.sqlite.util.Logger;
import me.growapet.libs.sqlite.util.LoggerFactory;
import me.growapet.libs.sqlite.util.ProcessRunner;

public class OSInfo {
    protected static ProcessRunner processRunner = new ProcessRunner();
    private static final HashMap<String, String> archMapping = new HashMap();
    public static final String X86 = "x86";
    public static final String X86_64 = "x86_64";
    public static final String IA64_32 = "ia64_32";
    public static final String IA64 = "ia64";
    public static final String PPC = "ppc";
    public static final String PPC64 = "ppc64";
    public static final String RISCV64 = "riscv64";

    public static void main(String[] args) {
        if (args.length >= 1) {
            if ("--os".equals(args[0])) {
                System.out.print(OSInfo.getOSName());
                return;
            }
            if ("--arch".equals(args[0])) {
                System.out.print(OSInfo.getArchName());
                return;
            }
        }
        System.out.print(OSInfo.getNativeLibFolderPathForCurrentOS());
    }

    public static String getNativeLibFolderPathForCurrentOS() {
        return OSInfo.getOSName() + "/" + OSInfo.getArchName();
    }

    public static String getOSName() {
        return OSInfo.translateOSNameToFolderName(System.getProperty("os.name"));
    }

    public static boolean isAndroid() {
        return OSInfo.isAndroidRuntime() || OSInfo.isAndroidTermux();
    }

    public static boolean isAndroidRuntime() {
        return System.getProperty("java.runtime.name", "").toLowerCase().contains("android");
    }

    public static boolean isAndroidTermux() {
        try {
            return processRunner.runAndWaitFor("uname -o").toLowerCase().contains("android");
        }
        catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isMusl() {
        boolean bl;
        block8: {
            Path mapFilesDir = Paths.get("/proc/self/map_files", new String[0]);
            Stream<Path> dirStream = Files.list(mapFilesDir);
            try {
                bl = dirStream.map(path -> {
                    try {
                        return path.toRealPath(new LinkOption[0]).toString();
                    }
                    catch (IOException e) {
                        return "";
                    }
                }).anyMatch(s -> s.toLowerCase().contains("musl"));
                if (dirStream == null) break block8;
            }
            catch (Throwable throwable) {
                try {
                    if (dirStream != null) {
                        try {
                            dirStream.close();
                        }
                        catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                catch (Exception ignored) {
                    return OSInfo.isAlpineLinux();
                }
            }
            dirStream.close();
        }
        return bl;
    }

    private static boolean isAlpineLinux() {
        boolean bl;
        block8: {
            Stream<String> osLines = Files.lines(Paths.get("/etc/os-release", new String[0]));
            try {
                bl = osLines.anyMatch(l -> l.startsWith("ID") && l.contains("alpine"));
                if (osLines == null) break block8;
            }
            catch (Throwable throwable) {
                try {
                    if (osLines != null) {
                        try {
                            osLines.close();
                        }
                        catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                catch (Exception exception) {
                    return false;
                }
            }
            osLines.close();
        }
        return bl;
    }

    static String getHardwareName() {
        try {
            return processRunner.runAndWaitFor("uname -m");
        }
        catch (Throwable e) {
            LogHolder.logger.error("Error while running uname -m", e);
            return "unknown";
        }
    }

    static String resolveArmArchType() {
        if (System.getProperty("os.name").contains("Linux")) {
            String armType = OSInfo.getHardwareName();
            if (OSInfo.isAndroid()) {
                if (armType.startsWith("aarch64")) {
                    return "aarch64";
                }
                return "arm";
            }
            if (armType.startsWith("armv6")) {
                return "armv6";
            }
            if (armType.startsWith("armv7")) {
                return "armv7";
            }
            if (armType.startsWith("armv5")) {
                return "arm";
            }
            if (armType.startsWith("aarch64")) {
                boolean is32bitJVM = "32".equals(System.getProperty("sun.arch.data.model"));
                if (is32bitJVM) {
                    return "armv7";
                }
                return "aarch64";
            }
            String abi = System.getProperty("sun.arch.abi");
            if (abi != null && abi.startsWith("gnueabihf")) {
                return "armv7";
            }
            String javaHome = System.getProperty("java.home");
            try {
                int exitCode = Runtime.getRuntime().exec("which readelf").waitFor();
                if (exitCode == 0) {
                    String[] cmdarray = new String[]{"/bin/sh", "-c", "find '" + javaHome + "' -name 'libjvm.so' | head -1 | xargs readelf -A | grep 'Tag_ABI_VFP_args: VFP registers'"};
                    exitCode = Runtime.getRuntime().exec(cmdarray).waitFor();
                    if (exitCode == 0) {
                        return "armv7";
                    }
                } else {
                    LogHolder.logger.warn("readelf not found. Cannot check if running on an armhf system, armel architecture will be presumed");
                }
            }
            catch (IOException | InterruptedException exception) {
                // empty catch block
            }
        }
        return "arm";
    }

    public static String getArchName() {
        String override = System.getProperty("me.growapet.libs.sqlite.osinfo.architecture");
        if (override != null) {
            return override;
        }
        String osArch = System.getProperty("os.arch");
        if (osArch.startsWith("arm")) {
            osArch = OSInfo.resolveArmArchType();
        } else {
            String lc = osArch.toLowerCase(Locale.US);
            if (archMapping.containsKey(lc)) {
                return archMapping.get(lc);
            }
        }
        return OSInfo.translateArchNameToFolderName(osArch);
    }

    static String translateOSNameToFolderName(String osName) {
        if (osName.contains("Windows")) {
            return "Windows";
        }
        if (osName.contains("Mac") || osName.contains("Darwin")) {
            return "Mac";
        }
        if (osName.contains("AIX")) {
            return "AIX";
        }
        if (OSInfo.isMusl()) {
            return "Linux-Musl";
        }
        if (OSInfo.isAndroid()) {
            return "Linux-Android";
        }
        if (osName.contains("Linux")) {
            return "Linux";
        }
        return osName.replaceAll("\\W", "");
    }

    static String translateArchNameToFolderName(String archName) {
        return archName.replaceAll("\\W", "");
    }

    static {
        archMapping.put(X86, X86);
        archMapping.put("i386", X86);
        archMapping.put("i486", X86);
        archMapping.put("i586", X86);
        archMapping.put("i686", X86);
        archMapping.put("pentium", X86);
        archMapping.put(X86_64, X86_64);
        archMapping.put("amd64", X86_64);
        archMapping.put("em64t", X86_64);
        archMapping.put("universal", X86_64);
        archMapping.put(IA64, IA64);
        archMapping.put("ia64w", IA64);
        archMapping.put(IA64_32, IA64_32);
        archMapping.put("ia64n", IA64_32);
        archMapping.put(PPC, PPC);
        archMapping.put("power", PPC);
        archMapping.put("powerpc", PPC);
        archMapping.put("power_pc", PPC);
        archMapping.put("power_rs", PPC);
        archMapping.put(PPC64, PPC64);
        archMapping.put("power64", PPC64);
        archMapping.put("powerpc64", PPC64);
        archMapping.put("power_pc64", PPC64);
        archMapping.put("power_rs64", PPC64);
        archMapping.put("ppc64el", PPC64);
        archMapping.put("ppc64le", PPC64);
        archMapping.put(RISCV64, RISCV64);
    }

    private static class LogHolder {
        private static final Logger logger = LoggerFactory.getLogger(OSInfo.class);

        private LogHolder() {
        }
    }
}

