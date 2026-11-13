package com.capgemini.test.code.users;

import com.capgemini.test.code.clients.CheckDniRequest;
import com.capgemini.test.code.clients.DniClient;
import com.capgemini.test.code.clients.NotificationClient;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository users;
  private final DniClient dniClient;
  private final NotificationClient notifications;

  public UserService(UserRepository users, DniClient dniClient,
                     NotificationClient notifications) {
    this.users = users;
    this.dniClient = dniClient;
    this.notifications = notifications;
  }

  @Transactional
  public CreatedUserResponse create(CreateUserRequest request) {
    if (users.existsByEmail(request.email())) {
      throw new UserValidationException("email");
    }
    if (users.existsByDni(request.dni())) {
      throw new UserValidationException("dni");
    }
    if (users.existsByPhone(request.phone())) {
      throw new UserValidationException("phone");
    }

    try {
      dniClient.check(new CheckDniRequest(request.dni()));
    } catch (FeignException exception) {
      throw new UserValidationException("dni");
    }

    User user = users.saveAndFlush(new User(request));
    // shortcut: synchronous delivery is not atomic with DB commit; use an outbox for durable retries.
    try {
      if ("admin".equals(request.rol())) {
        notifications.email(new NotificationClient.EmailRequest(request.email(), "usuario guardado"));
      } else {
        notifications.sms(new NotificationClient.SmsRequest(request.phone(), "usuario guardado"));
      }
    } catch (FeignException exception) {
      throw new UserValidationException("admin".equals(request.rol()) ? "email" : "phone");
    }
    return new CreatedUserResponse(user.getId());
  }
}
