package com.honeycomb.core.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Custom {@link OAuth2TokenValidator} that verifies a JWT contains the
 * expected audience ({@code aud}) claim.
 *
 * <p>If no audience is configured (blank or {@code null}), all tokens pass.
 * Otherwise the validator rejects tokens whose {@code aud} list does not
 * include the required value, returning an {@code invalid_token} error.</p>
 *
 * @see SecurityConfig#jwtDecoder(HoneycombSecurityProperties)
 */
public class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String audience;

    public JwtAudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (audience == null || audience.isBlank()) {
            return OAuth2TokenValidatorResult.success();
        }
        List<String> audiences = token.getAudience();
        if (audiences != null && audiences.contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error("invalid_token", "Token does not contain required audience", null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
