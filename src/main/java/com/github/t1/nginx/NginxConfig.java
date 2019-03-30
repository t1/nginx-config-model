package com.github.t1.nginx;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Wither;

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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.t1.nginx.HostPort.DEFAULT_HTTP_PORT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Value
@Wither
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

    @NonNull String before, after;
    @NonNull List<NginxServer> servers;
    @NonNull List<NginxUpstream> upstreams;

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

    public Stream<NginxServer> servers() { return servers.stream(); }

    public NginxConfig withoutUpstream(String name) {
        return withUpstreams(upstreams().filter(upstream -> !upstream.getName().equals(name)).collect(toList()));
    }

    public NginxConfig withUpstream(String name, Function<NginxUpstream, NginxUpstream> function) {
        NginxUpstream oldUpstream = upstream(name).orElseThrow(() -> new IllegalArgumentException("upstream not found " + name));
        NginxUpstream newUpstream = function.apply(oldUpstream);
        return withUpstream(list -> {
            list.remove(oldUpstream);
            list.add(newUpstream);
        });
    }

    public NginxConfig withUpstream(NginxUpstream upstream) {
        return withUpstream(list -> list.add(upstream));
    }

    private NginxConfig withUpstream(Consumer<List<NginxUpstream>> action) {
        List<NginxUpstream> list = new ArrayList<>(upstreams);
        action.accept(list);
        list.sort(null);
        return withUpstreams(list);
    }

    public NginxConfig withoutServer(String name) {
        return withServers(servers().filter(server -> !server.getName().equals(name)).collect(toList()));
    }

    public NginxConfig withServer(NginxServer server) {
        List<NginxServer> list = new ArrayList<>(servers);
        list.add(server);
        list.sort(null);
        return withServers(list);
    }


    /** https://www.nginx.com/resources/admin-guide/load-balancer/ */
    @Value
    @Wither
    public static class NginxUpstream implements Comparable<NginxUpstream> {
        private static final String PREFIX = "        server ";
        private static final String SUFFIX = ";\n";

        @NonNull String before;
        @NonNull String after;
        @NonNull String name;
        String method;
        List<HostPort> hostPorts;

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

        public NginxUpstream withoutHostPort(HostPort hostPort) {
            return withHostPorts(hostPorts.stream().filter(s -> !s.equals(hostPort)).collect(toList()));
        }

        public NginxUpstream withHostPort(HostPort hostPort) {
            return withHostPort(list -> list.add(hostPort));
        }

        public NginxUpstream withHostPort(int index, HostPort hostPort) {
            return withHostPort(index, old -> hostPort);
        }

        public NginxUpstream with(Function<HostPort, HostPort> function) {
            return withHostPorts(hostPorts().map(function).collect(toList()));
        }

        private NginxUpstream withHostPort(int index, Function<HostPort, HostPort> function) {
            HostPort oldHostPort = hostPorts.get(index);
            if (oldHostPort == null)
                throw new IllegalArgumentException("upstream not found " + name);
            HostPort newHostPort = function.apply(oldHostPort);
            return withHostPort(list -> {
                list.remove(oldHostPort);
                list.add(newHostPort);
                list.sort(null);
            });
        }

        private NginxUpstream withHostPort(Consumer<List<HostPort>> consumer) {
            List<HostPort> list = new ArrayList<>(hostPorts);
            consumer.accept(list);
            list.sort(null);
            return withHostPorts(list);
        }
    }

    @Value
    @Wither
    public static class NginxServer implements Comparable<NginxServer> {
        @NonNull String name;
        int listen;
        @NonNull List<NginxServerLocation> locations;

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

        public NginxServer withLocation(NginxServerLocation location) {
            List<NginxServerLocation> with = new ArrayList<>(locations);
            with.add(location);
            with.sort(null);
            return withLocations(with);
        }

        public NginxServer withoutLocation(NginxUpstream upstream) {
            return withLocations(
                locations().filter(location -> !location.passTo(upstream.getName())).collect(toList()));
        }
    }

    @Value
    @Wither
    public static class NginxServerLocation implements Comparable<NginxServerLocation> {
        @NonNull String before;
        @NonNull String after;
        @NonNull String name;
        URI proxyPass;

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

        boolean passTo(String upstreamName) { return proxyPass.getHost().equals(upstreamName); }
    }
}
