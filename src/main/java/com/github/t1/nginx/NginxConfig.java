package com.github.t1.nginx;

import lombok.*;
import lombok.experimental.Wither;

import java.net.*;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

@Value
@Builder
@Wither
public class NginxConfig {
    @SneakyThrows(MalformedURLException.class)
    public static NginxConfig readFrom(URI uri) { return readFrom(uri.toURL()); }

    static NginxConfig readFrom(URL url) { return NginxConfigParser.parse(url); }

    String before, after;
    List<NginxServer> servers;
    List<NginxUpstream> upstreams;


    @Override public String toString() {
        return before + toStrings(upstreams, "\n    ") + toStrings(servers, "") + after;
    }

    private String toStrings(List<?> list, String suffix) {
        return list.stream().map(Object::toString).collect(joining("\n    ", "", suffix));
    }


    public Stream<NginxUpstream> upstreams() { return upstreams.stream(); }

    public Stream<NginxServer> servers() { return servers.stream(); }

    public NginxConfig withoutUpstream(String name) {
        return withUpstreams(upstreams().filter(upstream -> !upstream.getName().equals(name)).collect(toList()));
    }

    public NginxConfig withUpstream(NginxUpstream upstream) {
        List<NginxUpstream> list = new ArrayList<>();
        list.addAll(upstreams);
        list.add(upstream);
        list.sort(null);
        return withUpstreams(list);
    }


    /** https://www.nginx.com/resources/admin-guide/load-balancer/ */
    @Value
    @Builder
    @Wither
    public static class NginxUpstream implements Comparable<NginxUpstream> {
        private static final String PREFIX = "        server ";
        private static final String SUFFIX = ";\n";

        String before, after;
        String name;
        String method;
        @Singular List<String> servers;

        @Override public String toString() {
            return "upstream " + name + " {\n"
                    + (before.isEmpty() ? "" : "        " + before + "\n")
                    + ((method == null) ? "" : "        " + method + ";\n\n")
                    + ((servers == null) ? ""
                               : servers.stream().collect(joining(SUFFIX + PREFIX, PREFIX, SUFFIX)))
                    + (after.isEmpty() ? "" : "        " + after + "\n")
                    + "    }\n";
        }

        public Stream<String> servers() { return servers.stream(); }

        public NginxUpstream withoutServer(String server) {
            return withServers(servers().filter(s -> !s.equals(server)).collect(toList()));
        }

        public NginxUpstream withServer(String server) {
            List<String> with = new ArrayList<>();
            with.addAll(servers);
            with.add(server);
            with.sort(null);
            return withServers(with);
        }

        @Override public int compareTo(NginxUpstream that) {
            return (that == null) ? -1 : this.name.compareTo(that.name);
        }
    }

    @Value
    @Builder
    public static class NginxServer {
        String name;
        String listen;
        @Singular List<NginxServerLocation> locations;

        @Override public String toString() {
            return "server {\n"
                    + "        server_name " + name + ";\n"
                    + "        listen " + listen + ";\n"
                    + locations.stream().map(NginxServerLocation::toString).collect(joining())
                    + "    }\n";
        }
    }

    @Value
    @Builder
    public static class NginxServerLocation {
        @NonNull String before;
        @NonNull String after;
        @NonNull String name;
        @NonNull String pass;

        @SuppressWarnings("unused")
        static class NginxServerLocationBuilder {
            String before = "", after = "";
        }

        @Override public String toString() {
            return "        location " + name + " {\n"
                    + (before.isEmpty() ? "" : "            " + before + "\n")
                    + "            proxy_pass " + pass + ";\n"
                    + (after.isEmpty() ? "" : "            " + after + "\n")
                    + "        }\n";
        }
    }
}
