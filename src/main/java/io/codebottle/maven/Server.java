package io.codebottle.maven;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;
import io.codebottle.api.CodeBottle;

public final class Server {
    public static Server SERVER;
    public static CodeBottle CODEBOTTLE;
    public static File TMP;

    private final HttpServer httpServer;

    private Server() {
        try {
            this.httpServer = HttpServer.create(InetSocketAddress.createUnresolved("localhost", 4226), 0);

            httpServer.createContext("/", ServerHandler.INSTANCE);
        } catch (Exception e) {
            throw new RuntimeException("Exception at Server Startup", e);
        }

        httpServer.start();
    }

    public static void main(String[] args) {
        System.out.println("Initializing HTTPServer...");
        SERVER = new Server();
        System.out.println("Initializing CodeBottle API...");
        CODEBOTTLE = CodeBottle.builder()
                //.token(System.getenv("CODEBOTTLE_TOKEN")) todo Uncomment when API key was added
                .build()
                .waitForLazyLoading();
        TMP = new File("/tmp/cb2m/");
        if (!TMP.exists() && TMP.mkdirs()) {
            TMP.deleteOnExit();
            System.out.printf("Created temporary server directory %s", TMP.getAbsolutePath());
            if (!TMP.canWrite())
                throw new IllegalStateException(String.format("Cannot write to temporary directory %s", TMP.getAbsolutePath()));
        }
    }
}
