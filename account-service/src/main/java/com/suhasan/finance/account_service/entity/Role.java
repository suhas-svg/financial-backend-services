package com.suhasan.finance.account_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.util.Set; 
import java.util.HashSet;

@Entity
@Table(name = "roles")
@Getter @Setter @NoArgsConstructor
public class Role {
  @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "roles_seq")
  @SequenceGenerator(name = "roles_seq", sequenceName = "roles_seq", allocationSize = 1)
  private Long id;

  @Column(unique = true, nullable = false)
  private String name;  // e.g. "ROLE_USER", "ROLE_ADMIN"
}
