package com.suhasan.finance.account_service.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver("10.0.0.10, 10.0.0.11");

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        assertThat(resolver.resolve(request("203.0.113.5", "1.2.3.4"))).isEqualTo("203.0.113.5");
    }

    @Test
    void usesRightMostUntrustedHopFromTrustedProxy() {
        assertThat(resolver.resolve(request("10.0.0.10", "198.51.100.7"))).isEqualTo("198.51.100.7");
        assertThat(resolver.resolve(request("10.0.0.10", "198.51.100.7, 10.0.0.11"))).isEqualTo("198.51.100.7");
    }

    @Test
    void clientCannotSpoofItsAddressByPrependingHops() {
        // The client sent "X-Forwarded-For: 1.1.1.1"; the proxy appended the real address.
        assertThat(resolver.resolve(request("10.0.0.10", "1.1.1.1, 198.51.100.7"))).isEqualTo("198.51.100.7");
    }

    @Test
    void fallsBackToPeerWhenHeaderIsMissingOrOnlyProxies() {
        assertThat(resolver.resolve(request("10.0.0.10", null))).isEqualTo("10.0.0.10");
        assertThat(resolver.resolve(request("10.0.0.10", "10.0.0.11"))).isEqualTo("10.0.0.10");
    }

    @Test
    void trustsNoProxyByDefault() {
        ClientIpResolver unconfigured = new ClientIpResolver("");
        assertThat(unconfigured.resolve(request("10.0.0.10", "198.51.100.7"))).isEqualTo("10.0.0.10");
    }

    @Test
    void supportsCidrRangesForDynamicProxyAddresses() {
        ClientIpResolver cidr = new ClientIpResolver("10.0.0.0/8, 172.16.0.0/12, fd00::/8");

        assertThat(cidr.resolve(request("10.42.7.3", "198.51.100.7"))).isEqualTo("198.51.100.7");
        assertThat(cidr.resolve(request("172.20.0.5", "1.1.1.1, 198.51.100.7, 10.42.7.3"))).isEqualTo("198.51.100.7");
        assertThat(cidr.resolve(request("fd12::1", "198.51.100.7"))).isEqualTo("198.51.100.7");
        // 172.32.x is outside 172.16.0.0/12, so its header is ignored.
        assertThat(cidr.resolve(request("172.32.0.5", "198.51.100.7"))).isEqualTo("172.32.0.5");
    }

    @Test
    void nonAddressHopsAreTreatedAsUntrusted() {
        ClientIpResolver cidr = new ClientIpResolver("10.0.0.0/8");
        assertThat(cidr.resolve(request("10.0.0.1", "unknown"))).isEqualTo("unknown");
    }

    @Test
    void rejectsMalformedTrustedProxyConfiguration() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ClientIpResolver("10.0.0.0/33"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ClientIpResolver("ingress.local"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MockHttpServletRequest request(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
