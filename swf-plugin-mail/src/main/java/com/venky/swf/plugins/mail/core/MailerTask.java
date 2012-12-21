package com.venky.swf.plugins.mail.core;

import java.util.List;

import javax.mail.Message.RecipientType;

import org.codemonkey.simplejavamail.Email;
import org.codemonkey.simplejavamail.Mailer;
import org.codemonkey.simplejavamail.TransportStrategy;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.UserEmail;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.mail.db.model.User;
import com.venky.swf.routing.Config;

public class MailerTask implements Task{

	private static final long serialVersionUID = 8083486775891668308L;
	
	int toUserId ;
	String subject; 
	String text; 
	public MailerTask(User to,String subject, String text){
		this.toUserId = to.getId();
		this.subject = subject;
		this.text = text;
	}
	
	public void execute() {
		User to = Database.getTable(User.class).get(toUserId);
		if (to == null){
			return;
		}
		List<UserEmail> emails = to.getUserEmails();
		if (emails.isEmpty()){
			throw new RuntimeException("No email available for " + to.getName());
		}
		
		String emailId = Config.instance().getProperty("swf.sendmail.user");
		String userName = Config.instance().getProperty("swf.sendmail.user.name");
		String password = Config.instance().getProperty("swf.sendmail.password");
		String host = Config.instance().getProperty("swf.sendmail.smtp.host");
		int port = Config.instance().getIntProperty("swf.sendmail.smtp.port");
		
		if( ObjectUtil.isVoid(emailId)) {
			throw new RuntimeException("Plugin not configured :swf.sendmail.user" );
		}
		
		UserEmail toEmail = emails.get(0);
		
		Email email = new Email();
		email.setFromAddress(userName, emailId);
		email.setSubject(subject);
		email.addRecipient(to.getName(), toEmail.getEmail(), RecipientType.TO);
		email.setTextHTML(text);
		
		Mailer mailer = new Mailer(host, port, emailId,password,TransportStrategy.SMTP_SSL); 
		mailer.sendMail(email);
		
	}

}