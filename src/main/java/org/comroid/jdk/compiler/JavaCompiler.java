package org.comroid.jdk.compiler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

public class JavaCompiler implements Runnable {
    private final List<String> sourcefiles;
    private final List<String> classpaths;
    private File destination;
    private String encoding;
    private String version;
    private String customOptions;

    { // defaults
        setEncoding(StandardCharsets.UTF_8);
        setVersion("1.8");
    }

    public JavaCompiler() {
        this.sourcefiles = new ArrayList<>();
        this.classpaths = new ArrayList<>();
    }

    public JavaCompiler addSourcefile(@Nullable File file) {
        if (file == null) return this;

        File[] subs;
        if (file.isDirectory() & (subs = file.listFiles()) != null)
            for (File sub : subs)
                addSourcefile(file);
        else sourcefiles.add(file.getAbsolutePath());

        return this;
    }

    public boolean removeSourcefile(Predicate<String> filter) {
        return sourcefiles.removeIf(filter);
    }

    public JavaCompiler addClasspath(File dir) {
        classpaths.add(checkDir(dir).getAbsolutePath());

        return this;
    }

    public boolean removeClasspath(Predicate<String> filter) {
        return classpaths.removeIf(filter);
    }

    public JavaCompiler setDestination(File dir) {
        destination = checkDir(dir);

        return this;
    }

    public JavaCompiler setEncoding(Charset charset) {
        encoding = charset.name();

        return this;
    }

    public JavaCompiler setVersion(String release) {
        version = release;

        return this;
    }

    public JavaCompiler setCustomOptions(String options) {
        customOptions = options;

        return this;
    }

    @Override
    public void run() {
        final StringBuilder command = new StringBuilder("javac ");

        if (classpaths.size() > 0) {
            classpaths.forEach(path -> command.append("-classpath ").append(path));
            command.append(' ');
        }

        if (destination != null) command.append("-d ")
                .append(destination.getAbsolutePath())
                .append(' ');

        if (version != null) command.append("-source ")
                .append(version)
                .append(' ');

        sourcefiles.forEach(path -> command.append(' ')
                .append(path));

        Process exec = null;
        try {
            exec = Runtime.getRuntime().exec(command.toString());

            exec.onExit().thenAcceptAsync(proc -> System.out.printf("Compiler process %s exited with code %d", proc, proc.exitValue()));

            System.out.printf("Transferring STDERR of compiler process %s to Systems STDOUT...", exec);
            exec.getErrorStream().transferTo(System.out);
        } catch (Throwable e) {
            System.out.printf("Unexpected %s occurred: %s. Aborting compilation.\n", e.getClass().getName(), e.getMessage());
            if (exec != null) exec.destroyForcibly();
            e.printStackTrace();
        }
    }

    private static File checkDir(File file) {
        if (file.isDirectory()) return file;

        throw new IllegalArgumentException(String.format("File %s is not a directory", file));
    }
}
