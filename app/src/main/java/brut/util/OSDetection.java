package brut.util;

public final class OSDetection {

    private static final String OS;
    private static final String BIT;

    static {
        String os = System.getProperty("os.name");
        if (os == null) {
            os = "Linux";
        }
        OS = os.toLowerCase();

        String bit = System.getProperty("sun.arch.data.model");
        if (bit == null) {
            bit = System.getProperty("os.arch", "arm64");
        }
        BIT = bit.toLowerCase();
    }

    private OSDetection() {}

    public static boolean isWindows() {
        return OS.contains("win");
    }

    public static boolean isMacOSX() {
        return OS.contains("mac");
    }

    public static boolean isUnix() {
        return OS.contains("nix") || OS.contains("nux") || OS.contains("aix") || OS.contains("sunos");
    }

    public static boolean is64Bit() {
        if (isWindows()) {
            String arch = System.getenv("PROCESSOR_ARCHITECTURE");
            String wow64 = System.getenv("PROCESSOR_ARCHITEW6432");
            return (arch != null && arch.endsWith("64")) || (wow64 != null && wow64.endsWith("64"));
        }
        return BIT.equals("64");
    }

    public static String returnOS() {
        return OS;
    }
}
