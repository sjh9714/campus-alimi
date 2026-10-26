package com.campusalimi.auth;

public interface MailSender {

    void send(String to, String subject, String body);
}
