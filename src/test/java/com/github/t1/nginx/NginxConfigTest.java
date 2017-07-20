package com.github.t1.nginx;

import com.github.t1.nginx.NginxConfig.*;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class NginxConfigTest {
    private static final NginxServer WORKER_LB = NginxServer
            .builder().name("worker").listen("80")
            .location(NginxServerLocation.builder().name("/").pass("http://backend")
                                         .after("proxy_set_header Host $host;\n"
                                                 + "            proxy_set_header X-Real-IP $remote_addr;")
                                         .build()).build();
    private static final NginxServer WORKER_01 = NginxServer
            .builder().name("worker01").listen("80")
            .location(NginxServerLocation.builder().name("/").pass("http://localhost:8180/").build()).build();
    private static final NginxServer WORKER_02 = NginxServer
            .builder().name("worker02").listen("80")
            .location(NginxServerLocation.builder().name("/").pass("http://localhost:8280/").build()).build();

    private static final NginxConfig config = NginxConfig.readFrom(NginxConfigTest.class.getResource("nginx.conf"));
    private static final NginxUpstream UPSTREAM = NginxUpstream
            .builder().name("backend").method("least_conn")
            .before("# lb-before-comment")
            .server("localhost:8180").server("localhost:8280")
            .after("# lb-after-comment")
            .build();

    @Test
    public void shouldRetainOriginalToString() throws Exception {
        assertThat(config.toString()).isEqualTo(contentOf(NginxConfigTest.class.getResource("nginx.conf")));
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
