package com.dasi.qa.agent.application.configuration;

import com.dasi.qa.agent.infrastructure.properties.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfiguration {

    @Bean
    public JavaMailSender javaMailSender(MailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setUsername(properties.getUsername());
        sender.setPassword(properties.getPassword());
        sender.setProtocol("smtps");
        sender.setDefaultEncoding("UTF-8");
        Properties mailProps = sender.getJavaMailProperties();
        mailProps.put("mail.smtps.auth", "true");
        mailProps.put("mail.smtps.ssl.enable", "true");
        mailProps.put("mail.smtps.timeout", "10000");
        return sender;
    }
}
