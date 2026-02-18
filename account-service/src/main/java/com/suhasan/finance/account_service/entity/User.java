package com.suhasan.finance.account_service.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
  @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "users_seq")
  @SequenceGenerator(name = "users_seq", sequenceName = "users_seq", allocationSize = 1)
  private Long id;

  @Column(unique = true, nullable = false)
  private String username;

  @Column(nullable = false)
  private String password;  // stores BCrypt hash

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "user_roles",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id"))
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private Set<Role> roles = new HashSet<>();

  public Set<Role> getRoles() {
    return new HashSet<>(roles);
  }

  public void setRoles(Set<Role> roles) {
    this.roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
  }
}
