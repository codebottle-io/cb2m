package io.codebottle.maven.exception;

import java.io.IOException;
import java.io.OutputStream;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import io.codebottle.api.rest.HTTPCodes;

public class ResponseHelper {
    public static void error(int code, HttpExchange httpExchange, Throwable t) throws IOException {
        final String jsonException = jsonException(t);

        write(httpExchange.getResponseBody(), jsonException);
        httpExchange.sendResponseHeaders(HTTPCodes.INTERNAL_SERVER_ERROR, jsonException.length());

        System.out.printf("Request with %s errored with body: \n%s\n\n", httpExchange, jsonException);
    }

    public static String jsonException(Throwable t) {
        final ObjectNode node = JsonNodeFactory.instance.objectNode();

        node.put("error", t.getMessage());
        node.putPOJO("error-pojo", t);

        return node.toPrettyString();
    }

    public static void write(OutputStream out, String str) throws IOException {
        for (char c : str.toCharArray())
            out.write(c);
    }
}
