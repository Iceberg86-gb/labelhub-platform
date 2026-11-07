package com.labelhub.agent.mcp;

public class McpAuthException extends RuntimeException {

    public enum Kind { UNAUTHORIZED, NOT_FOUND }

    private final Kind kind;

    public McpAuthException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
