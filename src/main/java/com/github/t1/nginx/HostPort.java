package com.github.t1.nginx;

import com.github.t1.nginx.NginxConfig.NginxServer;
import lombok.NonNull;
import lombok.Value;

import java.net.URI;
import java.util.Comparator;

@Value
public class HostPort implements Comparable<HostPort> {
    public static final int DEFAULT_HTTP_PORT = 80;

    String host;
    int port;

    static HostPort valueOf(String value) {
        String[] split = value.split(":", 2);
        return new HostPort(split[0], (split.length < 2) ? DEFAULT_HTTP_PORT : Integer.parseInt(split[1]));
    }

    public static HostPort of(URI uri) { return new HostPort(uri.getHost(), uri.getPort()); }

    @Override public String toString() { return host + ((port < 0) ? "" : (":" + port)); }

    @Override public int compareTo(@NonNull HostPort that) {
        return Comparator.comparing(HostPort::getHost).thenComparing(HostPort::getPort).compare(this, that);
    }

    boolean matches(NginxServer server) {
        return server.getName().equals(getHost()) && server.getListen() == getPort();
    }
}
