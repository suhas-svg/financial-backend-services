package com.suhasan.finance.account_service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves the address a request originated from, for security decisions such as
 * login throttling.
 *
 * X-Forwarded-For is only honoured when the direct peer is a configured trusted
 * proxy, and it is read right to left: proxies append the address they received
 * the request from, so the left-most entries are whatever the client sent and can
 * be forged. The first address that is not itself a trusted proxy is the client.
 *
 * Trusted proxies are addresses or CIDR ranges ({@code 10.0.0.0/8}), because
 * ingress controller and container addresses are usually dynamic.
 */
@Component
public class ClientIpResolver {

    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private final List<Cidr> trustedProxies;

    public ClientIpResolver(@Value("${security.client-ip.trusted-proxies:}") final String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Cidr::parse)
                .toList();
    }

    public String resolve(final HttpServletRequest request) {
        final String peer = request.getRemoteAddr();
        if (!isTrusted(peer)) {
            return peer;
        }
        final String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return peer;
        }
        final String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            final String hop = hops[i].trim();
            if (!hop.isEmpty() && !isTrusted(hop)) {
                return hop;
            }
        }
        return peer;
    }

    private boolean isTrusted(final String address) {
        if (trustedProxies.isEmpty()) {
            return false;
        }
        return parseLiteral(address)
                .map(parsed -> trustedProxies.stream().anyMatch(cidr -> cidr.contains(parsed)))
                .orElse(false);
    }

    /** Parses IP literals only, so header values can never trigger a DNS lookup. */
    private static Optional<byte[]> parseLiteral(final String value) {
        if (!IPV4_LITERAL.matcher(value).matches() && !value.contains(":")) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(value).getAddress());
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    private record Cidr(byte[] network, int prefixLength) {

        static Cidr parse(final String spec) {
            final int slash = spec.indexOf('/');
            final String address = slash < 0 ? spec : spec.substring(0, slash);
            final byte[] network = parseLiteral(address)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid trusted proxy address: " + spec));
            final int prefix = slash < 0 ? network.length * 8 : Integer.parseInt(spec.substring(slash + 1));
            if (prefix < 0 || prefix > network.length * 8) {
                throw new IllegalArgumentException("Invalid trusted proxy prefix: " + spec);
            }
            return new Cidr(network, prefix);
        }

        boolean contains(final byte[] address) {
            if (address.length != network.length) {
                return false;
            }
            for (int bit = 0; bit < prefixLength; bit++) {
                final int mask = 0x80 >>> (bit % 8);
                if ((address[bit / 8] & mask) != (network[bit / 8] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
