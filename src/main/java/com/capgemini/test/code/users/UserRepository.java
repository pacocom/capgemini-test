package com.capgemini.test.code.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {

  Optional<User> findByIdAndRoomId(Integer id, Long roomId);

  boolean existsByEmail(String email);

  boolean existsByDni(String dni);

  boolean existsByPhone(String phone);
}
