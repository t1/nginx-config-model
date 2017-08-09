package com.github.t1.nginx;

import com.github.t1.nginx.NginxConfig.*;
import org.junit.Test;

import java.net.*;
import java.util.List;

import static com.github.t1.nginx.HostPort.DEFAULT_HTTP_PORT;
import static org.assertj.core.api.Assertions.*;

public class NginxConfigTest {
    private static final NginxServer WORKER_LB = NginxServer
            .builder().name("worker").listen(DEFAULT_HTTP_PORT)
            .location(NginxServerLocation.builder().name("/").proxyPass(URI.create("http://backend"))
                                         .after("proxy_set_header Host $host;\n"
                                                 + "            proxy_set_header X-Real-IP $remote_addr;")
                                         .build())
            .location(NginxServerLocation.builder().name("/foo").proxyPass(URI.create("http://backend/foo")).build())
            .build();
    private static final NginxServer WORKER_01 = NginxServer
            .builder().name("worker01").listen(DEFAULT_HTTP_PORT)
            .location(NginxServerLocation.builder().name("/").proxyPass(URI.create("http://localhost:8180/")).build())
            .build();
    private static final NginxServer WORKER_02 = NginxServer
            .builder().name("worker02").listen(DEFAULT_HTTP_PORT)
            .location(NginxServerLocation.builder().name("/").proxyPass(URI.create("http://localhost:8280/")).build())
            .build();

    private static final URL RESOURCE = NginxConfigTest.class.getResource("nginx.conf");
    private static final NginxConfig config = NginxConfig.readFrom(RESOURCE);
    private static final NginxUpstream UPSTREAM = NginxUpstream
            .builder().name("backend").method("least_conn")
            .before("# lb-before-comment")
            .server(HostPort.valueOf("localhost:8180")).server(HostPort.valueOf("localhost:8280"))
            .after("# lb-after-comment")
            .build();

    @Test
    public void shouldRetainOriginalToString() throws Exception {
        assertThat(config.toString()).isEqualTo(contentOf(RESOURCE));
    }

    @Test
    public void shouldProvideServers() throws Exception {
        List<NginxServer> servers = config.getServers();

        assertThat(servers).containsExactly(WORKER_LB, WORKER_01, WORKER_02);
    }

    @Test
    public void shouldProvideUpstreams() throws Exception {
        List<NginxUpstream> upstreams = config.getUpstreams();

        assertThat(upstreams).containsExactly(UPSTREAM);
    }
}
