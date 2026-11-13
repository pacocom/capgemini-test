package com.capgemini.test.code.users;

public record UserResponse(Integer id, String name, String email, String phone,
                           String rol, String dni, Long roomId) {

  static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getName(), user.getEmail(),
        user.getPhone(), user.getRole(), user.getDni(), user.getRoomId());
  }
}
