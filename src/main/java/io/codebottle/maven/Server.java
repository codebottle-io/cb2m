package io.codebottle.maven;

import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;
import io.codebottle.api.CodeBottle;

public final class Server {
    public static Server SERVER;
    public static CodeBottle CODEBOTTLE;

    private final HttpServer httpServer;

    private Server() {
        try {
            this.httpServer = HttpServer.create(InetSocketAddress.createUnresolved("localhost", 80), 0);

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
    }
}