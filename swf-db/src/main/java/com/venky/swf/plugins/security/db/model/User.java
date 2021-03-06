package com.venky.swf.plugins.security.db.model;

import java.util.List;

import com.venky.swf.db.annotations.column.relationship.CONNECTED_VIA;

public interface User extends com.venky.swf.db.model.User{
	@CONNECTED_VIA("USER_ID")
	public List<UserRole> getUserRoles();
}
