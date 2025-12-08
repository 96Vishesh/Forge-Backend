package com.inn.automate.utils;


import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class EmailUtils {

    @Autowired
    private JavaMailSender emailSender;

    public void sendSimpleMessage(String to , String subject, String text , List<String> list) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("shelfsink01@gmail.com");
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        if(list != null && list.size() > 0)
            message.setTo(getCcArray(list));
        emailSender.send(message);
    }

    private String[] getCcArray(List<String> ccList) {
        String[] cc=new String[ccList.size()];
        for(int i=0;i<ccList.size();i++){
            cc[i]=ccList.get(i);
        }
        return cc;
    }

    public void forgotMail(String to, String subject, String password) throws MessagingException {
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom("shelfsink01@gmail.com");
        helper.setTo(to);
        helper.setSubject(subject);

        // Properly formatted HTML content
        String htmlMsg ="<p><b>Welcome Back to ShelfSync</b></p>"
                +"<p><b>Here are your Login details</b></p>"
                + "<p><b>Email:</b> " + to + "</p>"
                + "<p><b>Password:</b> " + password + "</p>"
                + "<p><a href='http://localhost:4200/'>Click here to login</a></p>";

        message.setContent(htmlMsg, "text/html");

        emailSender.send(message);
    }
//    @Async
//    public void sendRegistrationEmail(String to, String name) {
//        String subject = "Registration Successful - ShelfSync System";
//        String message = "Dear " + name + ",\n\nYour registration was successful. " +
//                "You will be able to log in once an admin approves your account.\n\nThank you,\nShelfSync Team";
//
//        sendSimpleMessage("noreply@shelfsync.com", subject, message, Collections.singletonList(to));
//    }



}

