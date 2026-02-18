package com.suhasan.finance.account_service.security;

import com.suhasan.finance.account_service.entity.User;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collection;
import java.util.stream.Collectors;


@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2", "SE_TRANSIENT_FIELD_NOT_RESTORED"},
    justification = "Spring Security user details wraps a managed entity reference and is not deserialized in this service"
)
public class CustomUserDetails implements UserDetails {
  private static final long serialVersionUID = 1L;
  private final transient User user;
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
