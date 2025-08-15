package com.suhasan.finance.account_service.security;

import com.suhasan.finance.account_service.entity.User;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collection;
import java.util.stream.Collectors;


public class CustomUserDetails implements UserDetails {
  private final User user;
  public CustomUserDetails(User user) { this.user = user; }

  @Override public Collection<? extends GrantedAuthority> getAuthorities() {
    return user.getRoles().stream()
      .map(r -> new SimpleGrantedAuthority(r.getName()))
      .collect(Collectors.toList());
  }
  @Override public String getPassword()   { return user.getPassword(); }
  @Override public String getUsername()   { return user.getUsername(); }
  @Override public boolean isAccountNonExpired()     { return true; }
  @Override public boolean isAccountNonLocked()      { return true; }
  @Override public boolean isCredentialsNonExpired() { return true; }
  @Override public boolean isEnabled()               { return true; }
}
