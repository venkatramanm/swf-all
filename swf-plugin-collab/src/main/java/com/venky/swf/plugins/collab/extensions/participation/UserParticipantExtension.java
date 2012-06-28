package com.venky.swf.plugins.collab.extensions.participation;

import java.util.Arrays;
import java.util.List;

import com.venky.core.collections.SequenceSet;
import com.venky.swf.db.extensions.ParticipantExtension;
import com.venky.swf.plugins.collab.db.model.participants.admin.Company;
import com.venky.swf.plugins.collab.db.model.user.User;
import com.venky.swf.pm.DataSecurityFilter;


public class UserParticipantExtension extends ParticipantExtension<User>{
	static {
		registerExtension(new UserParticipantExtension());
	}
	protected UserParticipantExtension() {
		super(User.class);
	}

	@Override
	protected List<Integer> getAllowedFieldValues(com.venky.swf.db.model.User user, User partial, String fieldName) {
		if ("SELF_USER_ID".equalsIgnoreCase(fieldName)) {
			SequenceSet<Integer> ret = new SequenceSet<Integer>();
			ret.add(user.getId());
			if (partial.getId() > 0 && partial.getCompanyId() != null && partial.getCompany().isAccessibleBy(user)){ 
				ret.add(partial.getId());
			}
			return ret.list();
		}else if ("COMPANY_ID".equalsIgnoreCase(fieldName)){
			if (partial.getCompanyId() != null && partial.getCompany().isAccessibleBy(user)){
				return Arrays.asList(partial.getCompanyId());
			}else {
				SequenceSet<Integer> ids =  DataSecurityFilter.getIds(DataSecurityFilter.getRecordsAccessible(Company.class,user));
				ids.add(null);
				return ids;
			}
		}
		return null;
	}

}
