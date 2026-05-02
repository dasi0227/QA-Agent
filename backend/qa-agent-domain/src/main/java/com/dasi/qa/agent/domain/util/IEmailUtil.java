package com.dasi.qa.agent.domain.util;

public interface IEmailUtil {

    void sendVerifyCode(String toEmail, String code);
}
