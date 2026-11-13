package com.capgemini.test.code.users;

public class UserValidationException extends RuntimeException {

  public UserValidationException(String field) {
    super("error validation " + field);
  }
}
