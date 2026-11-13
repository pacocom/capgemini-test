package com.capgemini.test.code.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

class UserExceptionHandlerTests {

  private final UserExceptionHandler handler = new UserExceptionHandler();

  @ParameterizedTest
  @CsvSource({"users_email_key,email", "users_dni_key,dni", "users_phone_key,phone"})
  void mapsDatabaseDuplicatesToConflict(String constraint, String field) {
    var exception = new DataIntegrityViolationException("duplicate",
        new ConstraintViolationException("duplicate", new SQLException(), constraint));
    var response = handler.duplicate(exception);
    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody()).isEqualTo(
        new UserExceptionHandler.ApiError(409, "error validation " + field));
  }

  @Test
  void doesNotMislabelUnrelatedDatabaseErrorsAsDuplicateUsers() {
    var exception = new DataIntegrityViolationException("unexpected constraint");
    assertThatThrownBy(() -> handler.duplicate(exception)).isSameAs(exception);
  }
}
