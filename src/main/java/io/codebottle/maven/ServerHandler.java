package io.codebottle.maven;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.time.temporal.ChronoField;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.codebottle.api.model.Snippet;
import io.codebottle.api.rest.HTTPCodes;

import static java.io.File.separatorChar;
import static io.codebottle.maven.exception.ResponseHelper.error;
import static io.codebottle.maven.exception.ResponseHelper.write;

public enum ServerHandler implements HttpHandler {
    INSTANCE;

    public static final Pattern SNIPPET_PATTERN = Pattern.compile("(https?://)?maven\\.codebottle\\.comroid\\.org/" +
            "io/codebottle/(?<username>.+)/(?<snippetId>.+)/(?<revisionId>\\d*)/.+");
    public static final Pattern EXPANDVAR_PATTERN = Pattern.compile(".*\\$\\{(?<varname>\\w+)}");
    public static final Pattern JAVA_CLASS_PATTERN = Pattern.compile("" +
            "package .+;\\n*" + // force io.codebottle.username package
            "\\t*public .*? class \\w[\\w\\d]* \\{\\n*" +
            "[.\\n\\t]*" +
            "}");
    public static final Pattern JAVA_METHOD_PATTERN = Pattern.compile("" +
            "public (static)? ((void)|(\\w[\\w\\d]*))\\(.*\\) \\{\\n*" +
            "[.\\n\\t]*" +
            "}");

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        final UUID uuid = UUID.randomUUID();

        final String uri = httpExchange.getRequestURI().toString();
        System.out.printf("Handling %s-Request @ %s", httpExchange.getRequestMethod(), uri);

        if (!httpExchange.getRequestMethod().equalsIgnoreCase("GET")) {
            error(HTTPCodes.BAD_REQUEST, httpExchange, new NoSuchMethodException("Only GET Method supported"));
            return;
        }

        final Matcher matcher = SNIPPET_PATTERN.matcher(uri);

        if (matcher.matches()) {
            final String username = matcher.group("username");
            final String snippetId = matcher.group("snippetId");
            final int revisionId = Integer.parseInt(matcher.group("revisionId"));

            final Optional<Snippet> snippetOpt = Server.CODEBOTTLE.getSnippetByID(snippetId);
            final Optional<Snippet.Revision> revisionOpt = snippetOpt
                    .filter(snippet -> snippet.getUsername().equals(username))
                    .flatMap(snippet -> snippet.getRevisionByID(revisionId));

            if (revisionOpt.isEmpty()) {
                error(HTTPCodes.NOT_FOUND, httpExchange, new NoSuchElementException(
                        String.format("No such snippet/revision: %s:%s:%s", username, snippetId, revisionId)));
                return;
            }

            final Snippet snippet = snippetOpt.get();
            final Snippet.Revision revision = revisionOpt.get();

            if (uri.endsWith(".xml")) {
                // pom was requested
                // get pom base resource
                final InputStream pomResource = ClassLoader.getSystemResourceAsStream("build/pom.xml");

                // check if pom base is null
                if (pomResource == null) {
                    error(HTTPCodes.INTERNAL_SERVER_ERROR, httpExchange, new NullPointerException("Could not find base POM resource"));
                    return;
                }

                // modify pom base
                final String pom = new BufferedReader(new InputStreamReader(pomResource)).lines()
                        .map(str -> pomLineConversion(str, snippet, revision))
                        .collect(Collectors.joining("\n"));

                // respond with modified pom
                write(httpExchange.getResponseBody(), pom);
                httpExchange.sendResponseHeaders(HTTPCodes.OK, pom.length());
            } else if (uri.endsWith(".jar")) {
                // artifact was requested
                // check snippet language [java]
                if (!revision.getLanguage().getName().equalsIgnoreCase("java")) {
                    error(HTTPCodes.BAD_REQUEST, httpExchange, new IllegalStateException("Snippet must be written in Java"));
                    return;
                }

                final String code = revision.getCode();
                SourceType sourceType;

                // check java sourcecode type [class;method]
                final Matcher classMatcher = JAVA_CLASS_PATTERN.matcher(code);
                final Matcher methodMatcher = JAVA_METHOD_PATTERN.matcher(code);

                if (classMatcher.matches()) {
                    if (code.startsWith("package io.codebottle." + username + ";"))
                        sourceType = SourceType.CLASS;
                    else {
                        error(HTTPCodes.INTERNAL_SERVER_ERROR, httpExchange, new IllegalStateException(
                                "Cannot autogenerate Artifact from snippet; class does not define package io.codebottle.<username> [io.codebottle." + username + "]"));
                        return;
                    }
                } else if (methodMatcher.matches()) sourceType = SourceType.METHOD;
                else {
                    error(HTTPCodes.INTERNAL_SERVER_ERROR, httpExchange, new IllegalStateException(
                            "Cannot autogenerate Artifact from snippet; invalid source code"));
                    return;
                }

                // prepare compiler environment
                final File dir = new File(Server.TMP.getAbsolutePath() + separatorChar + "job-compilation-" + uuid);
                if (!dir.mkdir()) error(HTTPCodes.INTERNAL_SERVER_ERROR, httpExchange, new IllegalStateException(
                        "Could not create job directory."));
                dir.deleteOnExit();

                final File build = new File(dir.getAbsolutePath() + separatorChar + "build");
                if (!dir.mkdir()) error(HTTPCodes.INTERNAL_SERVER_ERROR, httpExchange, new IllegalStateException(
                        "Could not create compiler build directory."));
                dir.deleteOnExit();

                final
                // TODO: 13.01.2020
                
                // compile
                // TODO: 13.01.2020

                // write output JAR file
                // TODO: 13.01.2020
            }
        }
    }

    private String pomLineConversion(String str, Snippet snippet, Snippet.Revision revision) {
        final Matcher matcher = EXPANDVAR_PATTERN.matcher(str);

        if (matcher.matches()) {
            final String varname = matcher.group("varname");
            String content = "null";

            switch (varname) {
                case "snippetId":
                    content = snippet.getID();
                    break;
                case "revisionId":
                    content = revision.getID();
                    break;
                case "snippetName":
                    content = snippet.getTitle();
                    break;
                case "revisionUrl":
                    content = String.format("https://codebottle.io/s/%s/revisions/%s", snippet.getID(), revision.getID());
                    break;
                case "revisionDateYear":
                    try {
                        content = String.valueOf(revision.getCreatedAt().get(ChronoField.YEAR));
                    } catch (Throwable ignored) {
                        content = "2020";
                    }
                    break;
            }

            str.replace("${" + varname + "}", content);
        }

        return str;
    }

    public enum SourceType {
        CLASS,
        METHOD
    }
}
