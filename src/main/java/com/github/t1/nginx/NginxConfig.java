package com.github.t1.nginx;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.github.t1.nginx.HostPort.DEFAULT_HTTP_PORT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

@Data
@Accessors(chain = true)
public class NginxConfig {
    @SneakyThrows(MalformedURLException.class)
    public static NginxConfig readFrom(URI uri) { return readFrom(uri.toURL()); }

    public static NginxConfig readFrom(Reader reader) { return NginxConfigParser.parse(reader); }

    static NginxConfig readFrom(URL url) {
        try (InputStream inputStream = url.openStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
            return NginxConfigParser.parse(reader);
        } catch (IOException e) {
            throw new RuntimeException("can't load config stream from '" + url + "'", e);
        }
    }

    @NonNull private String before, after;
    @NonNull private List<NginxServer> servers;
    @NonNull private List<NginxUpstream> upstreams;

    public static NginxConfig create() {
        return new NginxConfig("http {\n    ", "}", new ArrayList<>(), new ArrayList<>());
    }

    @SneakyThrows(IOException.class) public void writeTo(Path path) {
        Files.write(path, this.toString().getBytes());
    }


    @Override public String toString() {
        return before + toStrings(upstreams, "\n    ") + toStrings(servers, "") + after;
    }

    private String toStrings(List<?> list, String suffix) {
        return list.stream().map(Object::toString).collect(joining("\n    ", "", suffix));
    }


    public Stream<NginxUpstream> upstreams() { return upstreams.stream(); }

    public Optional<NginxUpstream> upstream(String name) {
        return upstreams().filter(upstream -> upstream.getName().equals(name)).findAny();
    }

    public Optional<NginxServer> server(HostPort hostPort) { return servers().filter(hostPort::matches).findAny(); }

    public Optional<NginxServer> server(String name) {
        return servers().filter(server -> server.getName().equals(name)).findAny();
    }

    private Stream<NginxServer> servers() { return servers.stream(); }

    public NginxConfig removeUpstream(String name) {
        upstreams.removeIf(upstream -> upstream.getName().equals(name));
        return this;
    }

    public NginxConfig addUpstream(NginxUpstream upstream) {
        upstreams.add(upstream);
        upstreams.sort(null);
        return this;
    }

    public NginxConfig removeServer(String name) {
        servers.removeIf(server -> server.getName().equals(name));
        return this;
    }

    public NginxConfig addServer(NginxServer server) {
        servers.add(server);
        servers.sort(null);
        return this;
    }


    /** https://www.nginx.com/resources/admin-guide/load-balancer/ */
    @Data
    @AllArgsConstructor
    public static class NginxUpstream implements Comparable<NginxUpstream> {
        private static final String PREFIX = "        server ";
        private static final String SUFFIX = ";\n";

        @NonNull private String before;
        @NonNull private String after;
        @NonNull private String name;
        private String method;
        private List<HostPort> hostPorts;

        public static NginxUpstream named(String name) {
            return new NginxUpstream("", "", name, null, new ArrayList<>());
        }

        @Override public int compareTo(NginxUpstream that) { return this.name.compareTo(that.name); }

        @Override public String toString() {
            return "upstream " + name + " {\n"
                + (before.isEmpty() ? "" : "        " + before + "\n")
                + ((method == null) ? "" : "        " + method + ";\n\n")
                + ((hostPorts == null) ? ""
                : hostPorts.stream().map(HostPort::toString)
                .collect(joining(SUFFIX + PREFIX, PREFIX, SUFFIX)))
                + (after.isEmpty() ? "" : "        " + after + "\n")
                + "    }\n";
        }

        public Stream<HostPort> hostPorts() { return hostPorts.stream(); }

        public NginxUpstream removeHostPort(HostPort hostPort) {
            hostPorts.removeIf(s -> s.equals(hostPort));
            return this;
        }

        public NginxUpstream addHostPort(HostPort hostPort) {
            hostPorts.add(hostPort);
            hostPorts.sort(null);
            return this;
        }

        public NginxUpstream setPort(HostPort hostPort, int port) {
            int index = hostPorts.indexOf(hostPort);
            if (index < 0) throw new IllegalArgumentException("can't find "+hostPort+" in "+this);
            hostPorts.set(index, hostPort.withPort(port));
            hostPorts.sort(null);
            return this;
        }
    }

    @Data
    @AllArgsConstructor
    public static class NginxServer implements Comparable<NginxServer> {
        @NonNull private String name;
        private int listen;
        @NonNull private List<NginxServerLocation> locations;

        public static NginxServer named(String name) {
            return new NginxServer(name, DEFAULT_HTTP_PORT, new ArrayList<>());
        }

        @Override public int compareTo(NginxServer that) { return this.name.compareTo(that.name); }


        @Override public String toString() {
            return "server {\n"
                + "        server_name " + name + ";\n"
                + "        listen " + listen + ";\n"
                + locations.stream().map(NginxServerLocation::toString).collect(joining())
                + "    }\n";
        }


        public Optional<NginxServerLocation> location(String name) {
            return locations().filter(location -> location.getName().equals(name)).findAny();
        }

        private Stream<NginxServerLocation> locations() { return locations.stream(); }

        public NginxServer addLocation(NginxServerLocation location) {
            locations.add(location);
            locations.sort(null);
            return this;
        }
    }

    @Data
    @AllArgsConstructor
    public static class NginxServerLocation implements Comparable<NginxServerLocation> {
        @NonNull private String before;
        @NonNull private String after;
        @NonNull private String name;
        private URI proxyPass;

        public static NginxServerLocation named(String name) {
            return new NginxServerLocation("", "", name, null);
        }

        @Override public int compareTo(NginxServerLocation that) { return this.name.compareTo(that.name); }

        @Override public String toString() {
            return "        location " + name + " {\n"
                + (before.isEmpty() ? "" : "            " + before + "\n")
                + "            proxy_pass " + proxyPass + ";\n"
                + (after.isEmpty() ? "" : "            " + after + "\n")
                + "        }\n";
        }
    }
}
