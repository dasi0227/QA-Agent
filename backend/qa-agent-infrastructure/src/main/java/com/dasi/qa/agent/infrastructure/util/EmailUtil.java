package com.dasi.qa.agent.infrastructure.util;

import com.dasi.qa.agent.domain.util.IEmailUtil;
import com.dasi.qa.agent.infrastructure.properties.MailProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class EmailUtil implements IEmailUtil {


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
            ClassPathResource resource = new ClassPathResource("template/verify-code-email.html");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("【邮件】验证码模板加载失败", e);
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
            log.info("【邮件】验证码发送成功: toEmail={}", toEmail);
        } catch (Exception e) {
            log.error("【邮件】验证码发送失败: toEmail={}", toEmail, e);
        }
    }
}
