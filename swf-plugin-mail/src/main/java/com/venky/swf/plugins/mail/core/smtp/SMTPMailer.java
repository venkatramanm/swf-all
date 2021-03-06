package com.venky.swf.plugins.mail.core.smtp;

import org.codemonkey.simplejavamail.Email;
import org.codemonkey.simplejavamail.Mailer;
import org.codemonkey.simplejavamail.TransportStrategy;

import com.venky.swf.routing.Config;

public class SMTPMailer implements com.venky.swf.plugins.mail.core.Mailer {

	private Mailer mailer = null;
	public SMTPMailer() {
		String emailId = Config.instance().getProperty("swf.sendmail.account");
		String password = Config.instance().getProperty("swf.sendmail.password");
		String host = Config.instance().getProperty("swf.sendmail.smtp.host","smtp.google.com");
		int port = Config.instance().getIntProperty("swf.sendmail.smtp.port",465);
		String transportStrategy = Config.instance().getProperty("swf.sendmail.smtp.auth", host.equals("smtp.google.com") ? TransportStrategy.SMTP_TLS.toString() : TransportStrategy.SMTP_PLAIN.toString());


		if (port == 465){
			mailer = new Mailer(host, port, emailId,password,TransportStrategy.SMTP_SSL);
		}else if (port == 587){
			mailer = new Mailer(host, port, emailId,password,TransportStrategy.valueOf(transportStrategy));
		}else {
			mailer = new Mailer(host, port, emailId,password,TransportStrategy.valueOf(transportStrategy));
		}
	}
	public void sendMail(Email email){
		mailer.sendMail(email);
	}
}
