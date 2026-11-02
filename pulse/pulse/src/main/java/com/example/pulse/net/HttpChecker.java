package com.example.pulse.net;

import com.example.pulse.exception.UnsafeUrlException;
import com.example.pulse.util.Formats;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Performs a single check against a URL. Never throws: every problem becomes a failed CheckOutcome. */
@Component
public class HttpChecker {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BODY_BYTES = 256 * 1024;

    private final UrlGuard guard;

    // Redirects are followed by hand so that every hop passes through the SSRF guard.
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public HttpChecker(UrlGuard guard) {
        this.guard = guard;
    }

    public CheckOutcome check(String url, int timeoutMs, Integer expectedStatus, String keyword) {
        long start = System.nanoTime();
        try {
            URI uri = guard.parse(url);
            for (int hop = 0; ; hop++) {
                guard.assertPublic(uri);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("User-Agent", "Pulse-Monitor/1.0")
                        .GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int code = response.statusCode();

                String location = response.headers().firstValue("Location").orElse(null);
                boolean redirect = code >= 300 && code < 400 && location != null
                        && !(expectedStatus != null && expectedStatus == code);
                if (redirect) {
                    close(response.body());
                    if (hop >= MAX_REDIRECTS) return failed(code, start, "Too many redirects");
                    uri = uri.resolve(location);
                    continue;
                }
                return evaluate(response, code, expectedStatus, keyword, start);
            }
        } catch (UnsafeUrlException e) {
            return failed(null, start, "Blocked: " + e.getMessage());
        } catch (HttpTimeoutException e) {
            return failed(null, start, "Timed out after " + timeoutMs + " ms");
        } catch (UnknownHostException e) {
            return failed(null, start, "Could not resolve host");
        } catch (IOException e) {
            return failed(null, start, describe(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed(null, start, "Check was interrupted");
        } catch (RuntimeException e) {
            return failed(null, start, "Unexpected error: " + e.getClass().getSimpleName());
        }
    }

    private CheckOutcome evaluate(HttpResponse<InputStream> response, int code, Integer expectedStatus,
                                  String keyword, long start) throws IOException {
        boolean statusOk = expectedStatus == null ? (code >= 200 && code < 300) : code == expectedStatus;
        if (!statusOk) {
            close(response.body());
            return failed(code, start, "HTTP " + code + " (expected " + (expectedStatus == null ? "2xx" : expectedStatus) + ")");
        }
        if (keyword != null && !keyword.isBlank()) {
            String body = readLimited(response.body());
            if (!body.toLowerCase().contains(keyword.toLowerCase())) {
                return failed(code, start, "Keyword not found in response");
            }
        } else {
            close(response.body());
        }
        return new CheckOutcome(true, code, elapsedMs(start), null);
    }

    private static String readLimited(InputStream in) throws IOException {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (out.size() < MAX_BODY_BYTES && (n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static void close(InputStream in) {
        try { in.close(); } catch (IOException ignored) { }
    }

    private static String describe(IOException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof UnknownHostException) return "Could not resolve host";
            if (t instanceof javax.net.ssl.SSLException) return "TLS/SSL error: " + Formats.truncate(t.getMessage(), 200);
        }
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return "Connection failed: " + Formats.truncate(msg, 200);
    }

    private static CheckOutcome failed(Integer code, long start, String error) {
        return new CheckOutcome(false, code, elapsedMs(start), Formats.truncate(error, 290));
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
