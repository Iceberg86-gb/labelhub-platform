package com.labelhub.api.module.ai.exception;

/** ADR-020 §3: enabling auto refused because the gate is not met (HTTP 409 AUTO_GATE_NOT_MET). */
public class AutoPolicyGateException extends RuntimeException {

    public AutoPolicyGateException(String message) {
        super(message);
    }
}
