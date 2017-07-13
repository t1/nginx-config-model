package com.github.t1.nginx;

import lombok.*;

import java.net.URL;
import java.util.List;

import static java.util.stream.Collectors.*;

@Getter
public class NginxConfig {
    public static NginxConfig readFrom(URL url) { return NginxConfigParser.parse(url); }

    String before, after;
    List<NginxServer> servers;


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

    @Override public String toString() { return before + serversToString() + after; }

    private String serversToString() {
        return servers.stream().map(NginxServer::toString).collect(joining("\n    "));
    }
}
