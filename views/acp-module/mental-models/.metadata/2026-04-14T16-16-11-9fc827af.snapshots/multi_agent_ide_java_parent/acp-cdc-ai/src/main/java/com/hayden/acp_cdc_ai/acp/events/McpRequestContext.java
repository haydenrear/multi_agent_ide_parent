package com.hayden.acp_cdc_ai.acp.events;

import org.springframework.http.HttpHeaders;

public final class McpRequestContext {

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    private McpRequestContext() {
    }

    public static void setHeaders(HttpHeaders headers) {
        if (headers == null) {
            CONTEXT.remove();
            return;
        }
        CONTEXT.set(new Context(headers));
    }

    public static HttpHeaders getHeaders() {
        Context context = CONTEXT.get();
        return context != null ? context.headers : null;
    }

    public static String getHeader(String name) {
        HttpHeaders headers = getHeaders();
        return headers != null ? headers.getFirst(name) : null;
    }

    public static void clear() {
        CONTEXT.remove();
    }

    private record Context(HttpHeaders headers) {
    }
}
