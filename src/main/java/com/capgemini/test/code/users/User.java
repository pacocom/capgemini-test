package com.capgemini.test.code.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  private String name;
  private String email;
  private String dni;
  private String phone;
  private String role;

  @Column(name = "room_id")
  private Long roomId;

  public User(CreateUserRequest request) {
    this.name = request.name();
    this.email = request.email();
    this.dni = request.dni();
    this.phone = request.phone();
    this.role = request.rol();
    this.roomId = 1L;
  }
}
