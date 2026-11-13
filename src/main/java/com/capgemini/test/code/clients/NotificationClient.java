package com.capgemini.test.code.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notificationClient", url = "${external.service.url}")
public interface NotificationClient {

  @PostMapping("/email")
  void email(@RequestBody EmailRequest request);

  @PostMapping("/sms")
  void sms(@RequestBody SmsRequest request);

  record EmailRequest(String email, String message) {
  }

  record SmsRequest(String phone, String message) {
  }
}
