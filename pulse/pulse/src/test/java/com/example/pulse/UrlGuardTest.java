package com.example.pulse;

import com.example.pulse.exception.UnsafeUrlException;
import com.example.pulse.net.UrlGuard;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Uses IP literals so no DNS lookups are needed. */
class UrlGuardTest {

    private final UrlGuard guard = new UrlGuard(8080);

    private void assertBlocked(String url) {
        assertThrows(UnsafeUrlException.class, () -> guard.assertPublic(URI.create(url)), url);
    }

    @Test
    void blocksPrivateAndInternalAddresses() {
        assertBlocked("http://10.0.0.1/");
        assertBlocked("http://192.168.1.1/");
        assertBlocked("http://172.16.5.4/");
        assertBlocked("http://127.0.0.1:9000/");
        assertBlocked("http://0.0.0.0/");
        assertBlocked("http://[::1]/");
        assertBlocked("http://100.64.0.1/");
    }

    @Test
    void blocksCloudMetadataAddress() {
        assertBlocked("http://169.254.169.254/latest/meta-data/");
    }

    @Test
    void blocksOtherSchemesAndCredentials() {
        assertBlocked("ftp://93.184.216.34/file");
        assertBlocked("file:///etc/passwd");
        assertBlocked("http://user:pass@93.184.216.34/");
    }

    @Test
    void allowsPublicAddresses() {
        assertDoesNotThrow(() -> guard.assertPublic(URI.create("https://93.184.216.34/")));
    }

    @Test
    void allowsOnlyTheOwnDemoEndpoints() {
        assertDoesNotThrow(() -> guard.assertPublic(URI.create("http://localhost:8080/demo/flaky")));
        assertBlocked("http://127.0.0.1:8080/api/monitors");
        assertBlocked("http://127.0.0.1:8080/demo/../api/monitors");
        assertBlocked("http://127.0.0.1:9999/demo/flaky");
    }

    @Test
    void parseRejectsGarbage() {
        assertThrows(UnsafeUrlException.class, () -> guard.parse(""));
        assertThrows(UnsafeUrlException.class, () -> guard.parse("not a url"));
        assertThrows(UnsafeUrlException.class, () -> guard.parse("example.com"));
    }
}
