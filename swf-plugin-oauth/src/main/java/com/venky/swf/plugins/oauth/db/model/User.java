package com.venky.swf.plugins.oauth.db.model;

import java.util.List;

import com.venky.swf.db.annotations.column.relationship.CONNECTED_VIA;
import com.venky.swf.db.model.Model;

public interface User extends Model{
	@CONNECTED_VIA("USER_ID")
	public List<UserEmail> getUserEmails();
}
