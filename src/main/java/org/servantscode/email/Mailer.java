package org.servantscode.email;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.servantscode.commons.db.ConfigDB;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Map;
import java.util.Properties;

import static javax.mail.Message.RecipientType.CC;
import static javax.mail.Message.RecipientType.TO;
import static javax.mail.internet.InternetAddress.parse;
import static org.servantscode.commons.ConfigUtils.decryptConfig;
import static org.servantscode.commons.StringUtils.isEmpty;
import static org.servantscode.commons.StringUtils.isSet;

public class Mailer {
    private static final Logger LOG = LogManager.getLogger(Mailer.class);
    private ConfigDB configDB;

    public Mailer() {
        this.configDB = new ConfigDB();
    }

    public void sendMail(Mail mail) {
        Authenticator auth = null;
        Map<String, String> mailConfig = configDB.getConfigurations("mail.smtp");

        if(Boolean.parseBoolean(mailConfig.get("mail.smtp.auth"))) {
            Map<String, String> userConfig = configDB.getConfigurations("mail.user");
            String emailUser = userConfig.get("mail.user.account");
            String emailPassword = decryptConfig(userConfig.get("mail.user.password"));

            if(isEmpty(emailUser) && isEmpty(emailPassword))
                throw new RuntimeException("Required auth credentials not configured");

            auth = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(emailUser, emailPassword);
                }
            };
        }

        Properties smtpConfig = new Properties();
        smtpConfig.putAll(mailConfig);

        Session session = Session.getInstance(smtpConfig, auth);

        try {
            Message message = generateMessage(session, mail);
            Transport.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Could not send email!!", e);
        }
    }

    // ----- Private -----
    private Message generateMessage(Session session, Mail mail) throws MessagingException {
        Message message = new MimeMessage(session);

        message.setFrom(new InternetAddress(mail.getFrom()));
        for (String email : mail.getTo())
            message.addRecipients(TO, parse(email));
        for (String email : mail.getCc())
            message.addRecipients(CC, parse(email));

        if(isSet(mail.getReplyTo()))
            message.setReplyTo(parse(mail.getReplyTo()));

        message.setSubject(mail.getSubject());

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(mail.getMessage(), "text/html");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);

        message.setContent(multipart);
        return message;
    }
}
