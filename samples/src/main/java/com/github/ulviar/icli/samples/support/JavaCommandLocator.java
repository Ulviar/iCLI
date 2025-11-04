package com.github.ulviar.icli.samples.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Utility that locates the current Java executable and classpath. */
public final class JavaCommandLocator {

    private JavaCommandLocator() {}

    public static Path javaExecutable() {
        String javaHome = System.getProperty("java.home");
        String bin = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        Path candidate = Paths.get(javaHome, "bin", bin);
        if (Files.exists(candidate)) {
            return candidate;
        }
        return Paths.get("java");
    }

    public static String currentClasspath() {
        return System.getProperty("java.class.path");
    }
}
