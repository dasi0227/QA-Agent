package com.dasi.qa.agent.infrastructure.util;

import com.dasi.qa.agent.infrastructure.properties.MailProperties;
import com.dasi.qa.agent.domain.util.IEmailUtil;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class EmailUtil implements IEmailUtil {

    private static final Logger log = LoggerFactory.getLogger(EmailUtil.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final String template;

    public EmailUtil(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.template = loadTemplate();
    }

    private String loadTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("templates/verify-code-email.html");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to load verify code email template", e);
            return "<html><body><p>你的验证码是：{{code}}</p></body></html>";
        }
    }

    @Override
    public void sendVerifyCode(String toEmail, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailProperties.getUsername());
            helper.setTo(toEmail);
            helper.setSubject("QA Agent 邮箱验证码");
            helper.setText(template.replace("{{code}}", code), true);
            mailSender.send(message);
            log.info("Verification code sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification code to {}", toEmail, e);
        }
    }
}
