package com.labelhub.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.agent.api.AiReviewApiClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * ADR-022 §1: POST /mcp (JSON-RPC, JSON responses), DELETE /mcp (end session), GET /mcp -> 405 (no SSE stream).
 * The API client blocks, so requests run on the bounded-elastic scheduler, never on the event loop.
 */
@Configuration
@ConditionalOnProperty(prefix = "labelhub.agent.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpHttpRoutes {

    public static final String SESSION_HEADER = "Mcp-Session-Id";

    @Bean
    McpServer mcpServer(AiReviewApiClient reviewApi, McpApiClient mcpApi, ObjectMapper objectMapper, Clock clock) {
        return new McpServer(mcpApi, new McpTools(reviewApi, mcpApi, objectMapper), objectMapper, clock);
    }

    @Bean
    RouterFunction<ServerResponse> mcpRoutes(McpServer server, ObjectMapper objectMapper) {
        return RouterFunctions.route()
            .POST("/mcp", request -> post(server, objectMapper, request))
            .DELETE("/mcp", request -> Mono.fromCallable(() -> server.close(bearer(request), request.headers().firstHeader(SESSION_HEADER)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(closed -> closed ? ServerResponse.noContent().build() : ServerResponse.notFound().build()))
            .GET("/mcp", request -> ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED).header("Allow", "POST, DELETE").build())
            .build();
    }

    private Mono<ServerResponse> post(McpServer server, ObjectMapper objectMapper, ServerRequest request) {
        return request.bodyToMono(String.class)
            .defaultIfEmpty("")
            .publishOn(Schedulers.boundedElastic())
            .map(body -> {
                JsonNode message;
                try {
                    message = objectMapper.readTree(body);
                } catch (Exception ex) {
                    message = null;
                }
                return server.handle(bearer(request), request.headers().firstHeader(SESSION_HEADER), message);
            })
            .flatMap(reply -> {
                ServerResponse.BodyBuilder builder = ServerResponse.status(reply.status());
                if (reply.status() == 401) {
                    builder.header("WWW-Authenticate", "Bearer realm=\"labelhub-mcp\"");
                }
                if (reply.sessionId() != null) {
                    builder.header(SESSION_HEADER, reply.sessionId());
                }
                return reply.body() == null ? builder.build() : builder.contentType(MediaType.APPLICATION_JSON).bodyValue(reply.body().toString());
            });
    }

    static String bearer(ServerRequest request) {
        String header = request.headers().firstHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return header.substring(7).trim();
    }
}
