package com.example.pulse.net;

import com.example.pulse.exception.UnsafeUrlException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * SSRF protection. Users can enter any URL, so without this a user could make the server request
 * http://localhost/..., an internal network address, or a cloud metadata endpoint (169.254.169.254).
 *
 * Rules: only http/https, no credentials in the URL, and every IP the host resolves to must be
 * public. The only exception is this app's own /demo/ endpoints, so the built-in demo monitor works.
 */
@Component
public class UrlGuard {

    private final int serverPort;

    public UrlGuard(@Value("${server.port:8080}") int serverPort) {
        this.serverPort = serverPort;
    }

    /** Syntax check only (scheme, host, no credentials). */
    public URI parse(String raw) {
        if (raw == null || raw.isBlank()) throw new UnsafeUrlException("URL is required");
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (URISyntaxException e) {
            throw new UnsafeUrlException("Invalid URL");
        }
        checkShape(uri);
        return uri;
    }

    /** Used when saving: syntax check, public-address check, and the host must exist. */
    public URI requirePublic(String raw) {
        URI uri = parse(raw);
        try {
            assertPublic(uri);
        } catch (UnknownHostException e) {
            throw new UnsafeUrlException("Could not resolve host: " + uri.getHost());
        }
        return uri;
    }

    /**
     * Used before every request (and every redirect hop). Throws UnsafeUrlException for blocked targets
     * and UnknownHostException when DNS fails (a normal "site is down" outcome during a check).
     */
    public void assertPublic(URI uri) throws UnknownHostException {
        checkShape(uri);
        if (isInternalDemo(uri)) return;
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (isPrivate(address)) {
                throw new UnsafeUrlException("Private or internal addresses are not allowed");
            }
        }
    }

    private void checkShape(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new UnsafeUrlException("Only http and https URLs are allowed");
        }
        if (uri.getHost() == null) throw new UnsafeUrlException("URL must include a host name");
        if (uri.getUserInfo() != null) throw new UnsafeUrlException("URLs with a username or password are not allowed");
    }

    private boolean isInternalDemo(URI uri) {
        String host = uri.getHost();
        boolean local = host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1");
        int port = uri.getPort() != -1 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        String path = uri.getPath() == null ? "" : uri.getPath();
        return local && port == serverPort && path.startsWith("/demo/") && !path.contains("..");
    }

    static boolean isPrivate(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF;
            return b0 == 0                                  // 0.0.0.0/8
                    || (b0 == 100 && b1 >= 64 && b1 <= 127) // 100.64.0.0/10 (carrier-grade NAT)
                    || (b0 == 198 && (b1 == 18 || b1 == 19)); // 198.18.0.0/15 (benchmarking)
        }
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;     // fc00::/7 (IPv6 unique local)
    }
}
