package com.venky.swf.plugins.collab.extensions.participation;

import java.util.ArrayList;
import java.util.List;

import com.venky.core.collections.SequenceSet;
import com.venky.swf.db.extensions.ParticipantExtension;
import com.venky.swf.db.model.User;
import com.venky.swf.plugins.collab.db.model.participants.admin.Facility;
import com.venky.swf.plugins.collab.db.model.user.UserFacility;
import com.venky.swf.pm.DataSecurityFilter;

public class UserFacilityParticipantExtension extends ParticipantExtension<UserFacility> {
	static {
		registerExtension(new UserFacilityParticipantExtension());
	}

	@Override
	protected List<Long> getAllowedFieldValues(User user, UserFacility model, String fieldName) {
		List<Long> ret = null;
		if (fieldName.equalsIgnoreCase("FACILITY_ID")){
			ret = new ArrayList<Long>();
			if (model.getFacilityId() > 0 ){
				if (model.getFacility().isAccessibleBy(user, Facility.class)){
					if (model.getUserId() == 0 || model.getFacility().isAccessibleBy(model.getUser(), Facility.class)){
						ret.add(model.getFacilityId());
					}
				}
			}else {
				List<Facility> facilites = DataSecurityFilter.getRecordsAccessible(Facility.class,user);
 				if (model.getUserId() > 0 ){
 					ret = new ArrayList<Long>();
					for (Facility f : facilites){
						if (f.isAccessibleBy(model.getUser(), Facility.class)){
							ret.add(f.getId());
						}
					}
				}else {
					ret = DataSecurityFilter.getIds(facilites);
				}
			}
		}else if (fieldName.equalsIgnoreCase("USER_ID")){
			if (model.getUserId() > 0){
				ret = new ArrayList<Long>();
				if (model.getUser().isAccessibleBy(user, User.class)){
					if (model.getFacilityId() == 0 || 
							(model.getFacility().isAccessibleBy(user, Facility.class) && 
									model.getFacility().isAccessibleBy(model.getUser(), Facility.class))){
						ret.add(model.getUserId());
					}
				}
			}else {
				ret = new SequenceSet<Long>();
				if (model.getFacilityId() > 0 && !model.getFacility().isAccessibleBy(user)){
					return ret; 
				}
				//Facility is accessible or not passed.
				
				Facility f = model.getFacility();
				if (f != null ){ 
					SequenceSet<Long> subscribedUserIds = new SequenceSet<>();
					for (UserFacility fu : f.getFacilityUsers()){
						subscribedUserIds.add(fu.getUserId());
					}
					SequenceSet<Long> subscribableUserIds = DataSecurityFilter.getIds(f.getCompany().getUsers());
					subscribableUserIds.removeAll(subscribedUserIds);
					ret.addAll(subscribableUserIds);
				}else {
					if (user.getRawRecord().getAsProxy(com.venky.swf.plugins.collab.db.model.user.User.class).isStaff()){
						return null;
					}else {
						ret.add(user.getId());
					}
				}
			}
		}
		return ret;	
	}


}
