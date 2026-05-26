package com.labelhub.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "labelhub.security")
public class SecurityProperties {

    private String jwtSecret;
    private long jwtTtlHours = 24;
    private String internalToken;

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public long getJwtTtlHours() { return jwtTtlHours; }
    public void setJwtTtlHours(long jwtTtlHours) { this.jwtTtlHours = jwtTtlHours; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
}
