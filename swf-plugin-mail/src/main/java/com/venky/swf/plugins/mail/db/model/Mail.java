package com.venky.swf.plugins.mail.db.model;

import java.io.Reader;
import java.sql.Timestamp;
import java.util.List;

import com.venky.swf.db.annotations.column.COLUMN_NAME;
import com.venky.swf.db.annotations.column.COLUMN_SIZE;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.db.annotations.model.EXPORTABLE;
import com.venky.swf.db.annotations.model.MENU;
import com.venky.swf.db.model.Model;

@MENU("")
@EXPORTABLE(false)
public interface Mail extends Model{
	
	@PARTICIPANT
	@Index
	public Long getUserId();
	public void setUserId(Long id);
	public User getUser();
	
	public String getEmail();
	public void setEmail(String email);

	@Index
	@COLUMN_SIZE(256)
	public String getSubject();
	public void setSubject(String title);
	
	public Reader getBody();
	public void setBody(Reader sBody);
	
	@COLUMN_NAME("CREATED_AT")
	public Timestamp getSentOn();
	public void setSentOn(Timestamp date);


	public List<MailAttachment> getMailAttachments();
}
