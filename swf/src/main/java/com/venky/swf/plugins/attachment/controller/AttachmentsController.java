package com.venky.swf.plugins.attachment.controller;

import java.util.List;

import com.venky.core.date.DateUtils;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.exceptions.AccessDeniedException;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.attachment.db.model.Attachment;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.View;

public class AttachmentsController extends ModelController<Attachment>{

	public AttachmentsController(Path path) {
		super(path);
	}


	public View view(String contentFileName){
		List<Attachment> attachements = new Select().from(Attachment.class).where(new Expression(getReflector().getPool(),"ATTACHMENT_CONTENT_NAME",Operator.EQ,contentFileName)).execute();
		if (attachements.size() == 1){
	        //getPath().getResponse().setDateHeader("Expires", DateUtils.addHours(System.currentTimeMillis(), 24*365*15));
			return super.view(attachements.get(0).getId());
		}
		throw new AccessDeniedException();
	}

	@Override
	@RequireLogin(false)
	public View view(long id) {
		return super.view(id);
	}

	@Override
	public View save() {
		if (getIntegrationAdaptor() == null){
			String id = (String)getPath().getFormFields().get("ID");
			if (!ObjectUtil.isVoid(id)){
				Attachment attachment = Database.getTable(getModelClass()).get(Long.valueOf(id));
				if (attachment != null){
					attachment.destroy();
					getPath().getFormFields().remove("ID");
				}
			}
		}
		return super.save();
	}
}
