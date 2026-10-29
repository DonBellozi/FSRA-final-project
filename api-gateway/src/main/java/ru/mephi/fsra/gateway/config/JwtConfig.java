package ru.mephi.fsra.gateway.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtConfig {

  @Bean
  public SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
    byte[] keyBytes = Base64.getDecoder().decode(secret);

    if (keyBytes.length < 32) {
      throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
    }

    return new SecretKeySpec(keyBytes, "HmacSHA256");
  }

  @Bean
  public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {

    JWK jwk = new OctetSequenceKey.Builder(jwtSecretKey).algorithm(JWSAlgorithm.HS256).build();

    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
  }

  @Bean
  public ReactiveJwtDecoder reactiveJwtDecoder(SecretKey jwtSecretKey) {
    return NimbusReactiveJwtDecoder.withSecretKey(jwtSecretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }
}
