package com.labelhub.api.module.agentaccess.service;

/** ADR-022: MCP authorization / trajectory refusals, mapped to 401, 404, 409 or 422. */
public class McpAccessException extends RuntimeException {

    public enum Kind { BAD_REQUEST, UNAUTHORIZED, NOT_FOUND, CONFLICT, UNPROCESSABLE }

    private final Kind kind;

    public McpAccessException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
