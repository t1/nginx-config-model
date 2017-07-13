package com.github.t1.nginx;

import com.github.t1.nginx.NginxConfig.*;
import com.github.t1.nginx.NginxConfig.NginxServer.NginxServerBuilder;
import com.github.t1.nginx.NginxConfig.NginxServerLocation.NginxServerLocationBuilder;
import com.github.t1.nginx.Tokenizer.Visitor;
import lombok.*;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.*;

class NginxConfigParser {
    static NginxConfig parse(URL url) {
        try (InputStream inputStream = url.openStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
            NginxConfigParser parser = new NginxConfigParser(reader);
            return parser.build();
        } catch (IOException e) {
            throw new RuntimeException("can't load config stream from '" + url + "'");
        }
    }

    private StringBuilder before = new StringBuilder();
    private StringBuilder after = new StringBuilder();
    private StringBuilder current = before;
    private List<NginxServer> servers = new ArrayList<>();

    private NginxConfigParser(Reader reader) { new Tokenizer(reader).accept(new RootVisitor()); }

    private class StringVisitor extends Visitor {
        @Override public Visitor visitWhitespace(String whitespace) {
            current.append(whitespace);
            return this;
        }

        @Override public Visitor visitToken(String token) {
            current.append(token);
            return this;
        }

        @Override public Visitor startBlock() {
            current.append("{");
            return this;
        }

        @Override public Visitor endBlock() {
            current.append("}");
            return this;
        }
    }

    private class RootVisitor extends StringVisitor {
        @Override public Visitor visitToken(String token) {
            if ("http".equals(token)) {
                current.append("http");
                return new HttpVisitor();
            } else {
                return super.visitToken(token);
            }
        }
    }

    private class HttpVisitor extends StringVisitor {
        private final List<ServerVisitor> serverVisitors = new ArrayList<>();

        @Override public Visitor visitToken(String token) {
            if ("server".equals(token)) {
                current = null;
                ServerVisitor serverVisitor = new ServerVisitor(this);
                serverVisitors.add(serverVisitor);
                return serverVisitor;
            } else {
                if (current == null) {
                    current = after;
                    current.append("\n    ");
                }
                return super.visitToken(token);
            }
        }

        @Override public Visitor visitWhitespace(String whitespace) {
            if (current == null)
                return this;
            return super.visitWhitespace(whitespace);
        }

        @Override public Visitor endBlock() {
            for (ServerVisitor server : serverVisitors)
                servers.add(server.server.build());
            super.endBlock();
            return new RootVisitor();
        }
    }

    private static class ServerVisitor extends Visitor {
        private Visitor next;
        private NginxServerBuilder server = NginxServer.builder();

        ServerVisitor(Visitor next) { this.next = next; }

        @Override public Visitor visitToken(String token) {
            if ("server_name".equals(token)) {
                return new ValueVisitor(this, value -> server.name(value));
            } else if ("listen".equals(token)) {
                return new ValueVisitor(this, value -> server.listen(value));
            } else if ("location".equals(token)) {
                return new LocationVisitor(this, server);
            } else {
                return super.visitToken(token);
            }
        }

        @Override public Visitor endBlock() { return next; }
    }

    @RequiredArgsConstructor
    private static class LocationVisitor extends Visitor {
        private final Visitor next;
        private final NginxServerBuilder server;
        private NginxServerLocationBuilder location = NginxServerLocation.builder();

        @Override public Visitor visitToken(String token) {
            if ("proxy_pass".equals(token)) {
                return new ValueVisitor(this, value -> location.pass(value));
            } else
                return this;
        }

        @Override public Visitor endBlock() {
            server.location(location.build());
            return next;
        }
    }

    @AllArgsConstructor
    private static class ValueVisitor extends Visitor {
        private Visitor next;
        private Consumer<String> consumer;

        @Override public Visitor visitToken(String token) {
            assert token.endsWith(";");
            consumer.accept(token.substring(0, token.length() - 1));
            return next;
        }
    }

    private NginxConfig build() {
        NginxConfig result = new NginxConfig();
        result.before = before.toString();
        result.after = after.toString();
        result.servers = servers;
        return result;
    }
}
