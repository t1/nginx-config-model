package com.github.t1.nginx;

import com.github.t1.nginx.NginxConfig.NginxServer;
import com.github.t1.nginx.NginxConfig.NginxServerLocation;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
import com.github.t1.nginx.Tokenizer.NamedBlockNameVisitor;
import com.github.t1.nginx.Tokenizer.NamedBlockVisitor;
import com.github.t1.nginx.Tokenizer.ValueVisitor;
import com.github.t1.nginx.Tokenizer.Visitor;
import lombok.Setter;

import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class NginxConfigParser {
    static NginxConfig parse(Reader reader) { return new NginxConfigParser(reader).build(); }

    private StringBuilder before = new StringBuilder();
    private StringBuilder after = new StringBuilder();
    private StringBuilder current = before;
    private List<NginxServer> servers = new ArrayList<>();
    private List<NginxUpstream> upstreams = new ArrayList<>();

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
        private final List<UpstreamVisitor> upstreamVisitors = new ArrayList<>();
        private final List<ServerVisitor> serverVisitors = new ArrayList<>();

        @Override public Visitor visitToken(String token) {
            if ("upstream".equals(token)) {
                current = null;
                UpstreamVisitor upstreamVisitor = new UpstreamVisitor(this);
                upstreamVisitors.add(upstreamVisitor);
                return new NamedBlockNameVisitor(upstreamVisitor);
            } else if ("server".equals(token)) {
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
            for (UpstreamVisitor upstream : upstreamVisitors)
                upstreams.add(upstream.upstream);
            for (ServerVisitor server : serverVisitors)
                servers.add(server.server);
            if (current == null)
                current = after;
            super.endBlock();
            return new RootVisitor();
        }
    }

    private static class UpstreamVisitor extends NamedBlockVisitor {
        private NginxUpstream upstream;

        private UpstreamVisitor(Visitor next) { super(next); }

        @Override void setName(String name) { upstream = NginxUpstream.named(name); }

        @Override public Visitor visitToken(String token) {
            if ("least_conn;".equals(token)) {
                upstream.setMethod("least_conn");
            } else if ("server".equals(token)) {
                return new ValueVisitor(this, value -> {
                    upstream.addHostPort(HostPort.valueOf(value));
                    toAfter();
                });
            } else {
                append(token);
            }
            return this;
        }

        @Override public Visitor visitWhitespace(String whitespace) {
            append(whitespace);
            return this;
        }

        @Override public Visitor endBlock() {
            upstream.setBefore(before()).setAfter(after());
            return next();
        }
    }

    private static class ServerVisitor extends Visitor {
        private Visitor next;
        @Setter private NginxServer server;

        ServerVisitor(Visitor next) { this.next = next; }

        @Override public Visitor visitToken(String token) {
            if ("server_name".equals(token)) {
                return new ValueVisitor(this, value -> server = NginxServer.named(value));
            } else if ("listen".equals(token)) {
                return new ValueVisitor(this, value -> server = readListen(value));
            } else if ("location".equals(token)) {
                return new NamedBlockNameVisitor(new LocationVisitor(this, server, this::setServer));
            } else {
                return super.visitToken(token);
            }
        }

        private NginxServer readListen(String value) {
            if (server == null)
                return null;
            if (value.contains(":"))
                value = value.substring(value.indexOf(':') + 1);
            return server.setListen(Integer.parseInt(value));
        }

        @Override public Visitor endBlock() { return next; }
    }

    private static class LocationVisitor extends NamedBlockVisitor {
        private NginxServer server;
        private NginxServerLocation location;
        private final Consumer<NginxServer> result;

        private LocationVisitor(Visitor next, NginxServer server, Consumer<NginxServer> result) {
            super(next);
            this.server = server;
            this.result = result;
        }

        @Override void setName(String name) { location = NginxServerLocation.named(name); }

        @Override public Visitor visitToken(String token) {
            if ("proxy_pass".equals(token)) {
                return new ValueVisitor(this, value -> location.setProxyPass(URI.create(value)));
            } else {
                toAfter();
                append(token);
            }
            return this;
        }

        @Override public Visitor visitWhitespace(String whitespace) {
            append(whitespace);
            return this;
        }

        @Override public Visitor endBlock() {
            result.accept(server.addLocation(location.setBefore(before()).setAfter(after())));
            return next();
        }
    }

    private NginxConfig build() {
        return NginxConfig.create()
            .setBefore(before.toString())
            .setUpstreams(upstreams)
            .setServers(servers)
            .setAfter(trimLastNl(after.toString()));
    }

    private static String trimLastNl(String string) {
        return (string.endsWith("\n")) ? string.substring(0, string.length() - 1) : string;
    }
}
