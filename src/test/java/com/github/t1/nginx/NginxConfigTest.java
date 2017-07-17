package com.github.t1.nginx;

import com.github.t1.nginx.NginxConfig.*;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class NginxConfigTest {
    private static final NginxServer WORKER_01 = NginxServer
            .builder().name("worker01").listen("80")
            .location(NginxServerLocation.builder().pass("http://localhost:8180/").build()).build();
    private static final NginxServer WORKER_02 = NginxServer
            .builder().name("worker02").listen("80")
            .location(NginxServerLocation.builder().pass("http://localhost:8280/").build()).build();

    private static final NginxConfig config = NginxConfig.readFrom(NginxConfigTest.class.getResource("nginx.conf"));

    @Test
    public void shouldRetainOriginalToString() throws Exception {
        assertThat(config.toString()).isEqualTo(contentOf(NginxConfigTest.class.getResource("nginx.conf")));
    }

    @Test
    public void shouldProvideServers() throws Exception {
        List<NginxServer> servers = config.getServers();

        assertThat(servers).containsExactly(WORKER_01, WORKER_02);
    }
}
