package com.shopifyplus.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// One row per issued refresh token. Opaque (not a JWT) so revocation is a DB fact,
// not something the client can forge. Rotated on every use.
@Document(collection = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    @Indexed
    private String userId;

    private Instant expiresAt;

    // Set true when rotated or on logout; a revoked token can never mint access again.
    private boolean revoked;

    private Instant createdAt;
}
