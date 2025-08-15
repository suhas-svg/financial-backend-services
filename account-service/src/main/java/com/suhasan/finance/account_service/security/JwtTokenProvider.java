package com.suhasan.finance.account_service.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {
  @Value("${security.jwt.secret}")  
  private String jwtSecret;

  @Value("${security.jwt.expiration-in-ms}")
  private long jwtExpirationInMs;

  public String generateToken(Authentication auth) {
    Instant now = Instant.now();
    Instant exp = now.plusMillis(jwtExpirationInMs);
    
    List<String> roles = auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());
    
    return Jwts.builder()
      .setSubject(auth.getName())
      .setIssuedAt(Date.from(now))
      .setExpiration(Date.from(exp))
      .claim("roles", roles)
      .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
      .compact();
  }

  public String getUsernameFromJWT(String token) {
    return Jwts.parserBuilder()
      .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
      .build()
      .parseClaimsJws(token)
      .getBody()
      .getSubject();
  }

  public boolean validateToken(String token) {
    try {
      Jwts.parserBuilder()
        .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
        .build()
        .parseClaimsJws(token);
      return true;
    } catch (JwtException | IllegalArgumentException ex) {
      return false;
    }
  }
}