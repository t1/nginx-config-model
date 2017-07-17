package com.github.t1.nginx;

import lombok.*;

import java.net.*;
import java.util.List;

import static java.util.stream.Collectors.*;

@Getter
public class NginxConfig {
    @SneakyThrows(MalformedURLException.class)
    public static NginxConfig readFrom(URI uri) { return readFrom(uri.toURL()); }

    public static NginxConfig readFrom(URL url) { return NginxConfigParser.parse(url); }

    String before, after;
    List<NginxServer> servers;
    List<NginxUpstream> upstreams;


    @Override public String toString() {
        return before + toStrings(upstreams, "\n    ") + toStrings(servers, "") + after;
    }

    private String toStrings(List<?> list, String suffix) {
        return list.stream().map(Object::toString).collect(joining("\n    ", "", suffix));
    }


    /** https://www.nginx.com/resources/admin-guide/load-balancer/ */
    @Value
    @Builder
    public static class NginxUpstream {
        private static final String PREFIX = "        server ";
        private static final String SUFFIX = ";\n";

        String name;
        String method;
        @Singular List<String> servers;

        @Override public String toString() {
            return "upstream " + name + " {\n"
                    + ((method == null) ? "" : "        " + method + ";\n\n")
                    + ((servers == null) ? ""
                               : servers.stream().collect(joining(SUFFIX + PREFIX, PREFIX, SUFFIX)))
                    + "    }\n";
        }
    }

    @Value
    @Builder
    public static class NginxServer {
        String name;
        String listen;
        NginxServerLocation location;

        @Override public String toString() {
            return "server {\n"
                    + "        server_name " + name + ";\n"
                    + "        listen      " + listen + ";\n"
                    + location
                    + "    }\n";
        }
    }

    @Value
    @Builder
    public static class NginxServerLocation {
        String pass;

        @Override public String toString() {
            return "        location / {\n"
                    + "            proxy_pass   " + pass + ";\n"
                    + "        }\n";
        }
    }
}
