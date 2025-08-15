package com.suhasan.finance.account_service.security;

import com.suhasan.finance.account_service.repository.UserRepository;
import com.suhasan.finance.account_service.entity.User;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;



@Service
public class CustomUserDetailsService implements UserDetailsService {
  private final UserRepository userRepo;
  public CustomUserDetailsService(UserRepository userRepo) {
    this.userRepo = userRepo;
  }

  @Override
  public UserDetails loadUserByUsername(String username)
      throws UsernameNotFoundException {
    User user = userRepo.findByUsername(username)
        .orElseThrow(() ->
          new UsernameNotFoundException("User not found: " + username));
    return new CustomUserDetails(user);
  }
}
