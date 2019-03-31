package com.github.t1.nginx;

import com.github.t1.nginx.NginxConfig.NginxServer;
import com.github.t1.nginx.NginxConfig.NginxServerLocation;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

class NginxConfigTest {
    private static final NginxServer WORKER_LB = NginxServer
        .named("worker")
        .addLocation(NginxServerLocation.named("/").setProxyPass(URI.create("http://backend"))
            .setAfter("proxy_set_header Host $host;\n"
                + "            proxy_set_header X-Real-IP $remote_addr;"))
        .addLocation(NginxServerLocation.named("/foo").setProxyPass(URI.create("http://backend/foo")));
    private static final NginxServer WORKER_01 = NginxServer
        .named("worker01")
        .addLocation(NginxServerLocation.named("/").setProxyPass(URI.create("http://localhost:8180/")));
    private static final NginxServer WORKER_02 = NginxServer
        .named("worker02")
        .addLocation(NginxServerLocation.named("/").setProxyPass(URI.create("http://localhost:8280/")));

    private static final URL RESOURCE = NginxConfigTest.class.getResource("nginx.conf");
    private static final NginxConfig config = NginxConfig.readFrom(RESOURCE);
    private static final NginxUpstream UPSTREAM = NginxUpstream
        .named("backend").setMethod("least_conn")
        .setBefore("# lb-before-comment")
        .addHostPort(HostPort.valueOf("localhost:8180"))
        .addHostPort(HostPort.valueOf("localhost:8280"))
        .setAfter("# lb-after-comment");

    @Test void shouldRetainOriginalToString() {
        assertThat(config.toString()).isEqualTo(contentOf(RESOURCE));
    }

    @Test void shouldProvideServers() {
        List<NginxServer> servers = config.getServers();

        assertThat(servers).containsExactly(WORKER_LB, WORKER_01, WORKER_02);
    }

    @Test void shouldProvideUpstreams() {
        List<NginxUpstream> upstreams = config.getUpstreams();

        assertThat(upstreams).containsExactly(UPSTREAM);
    }
}
