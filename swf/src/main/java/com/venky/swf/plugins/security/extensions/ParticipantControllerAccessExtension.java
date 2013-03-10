package com.venky.swf.plugins.security.extensions;

import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.venky.cache.Cache;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.User;
import com.venky.swf.db.table.BindVariable;
import com.venky.swf.db.table.Table;
import com.venky.swf.exceptions.AccessDeniedException;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.security.db.model.RolePermission;
import com.venky.swf.plugins.security.db.model.UserRole;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.sql.parser.SQLExpressionParser;
import com.venky.swf.sql.parser.XMLExpressionParser;

public class ParticipantControllerAccessExtension implements Extension{
	static {
		Registry.instance().registerExtension(Path.ALLOW_CONTROLLER_ACTION, new ParticipantControllerAccessExtension());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void invoke(Object... context) {
		User user = (User)context[0];
		if (user != null && user.isAdmin()){
			return;
		}
		String controllerPathElementName = (String)context[1];
		String actionPathElementName = (String)context[2];
		String parameterValue = (String)context[3];
		Path tmpPath = new Path("/"+controllerPathElementName+"/"+actionPathElementName+"/"+parameterValue);
		boolean securedAction = false;
		for (Method m : tmpPath.getActionMethods(actionPathElementName, parameterValue)){
			securedAction = tmpPath.isSecuredAction(m);
			if (securedAction){
				break;
			}
		}
		if (!securedAction){
			return;
		}else if (user == null){
			throw new AccessDeniedException();
		}
		
		Class<? extends Model> modelClass  = null;
		List<String> participantingRoles = new ArrayList<String>();
		Model selectedModel = null;

		Table possibleTable = Path.getTable(controllerPathElementName);
		if ( possibleTable != null ){
			modelClass = possibleTable.getModelClass();
		}
		if (modelClass != null ){
			Cache<String,Map<String,List<Integer>>> pGroupOptions = user.getParticipationOptions(modelClass);
			if (parameterValue != null){
				try {
					int id = Integer.valueOf(parameterValue);
					selectedModel = possibleTable.get(id);
					if (selectedModel != null){
						participantingRoles.addAll(selectedModel.getParticipatingRoles(user, pGroupOptions));
					}
				}catch (NumberFormatException ex){
					//
				}catch (IllegalArgumentException ex) {
					throw new RuntimeException(ex);
				}
			}else {
				Set<String> fields = new HashSet<String>();
				for (String g: pGroupOptions.keySet()){
					fields.addAll(pGroupOptions.get(g).keySet());
				}
				for (String referencedModelIdFieldName :fields){
					participantingRoles.add(referencedModelIdFieldName.substring(0, referencedModelIdFieldName.length()-3));
				}
			}
		}

		Expression permissionQueryWhere = new Expression(Conjunction.AND);

		Expression participationWhere = new Expression(Conjunction.OR);
		participationWhere.add(new Expression("participation",Operator.EQ));
		for (String participatingRole:participantingRoles){
			participationWhere.add(new Expression("participation",Operator.EQ,new BindVariable(participatingRole)));
		}
		permissionQueryWhere.add(participationWhere);
		
		boolean defaultController = false;
		if (ObjectUtil.isVoid(controllerPathElementName)){
			defaultController = true;
		}
		
		Expression controllerActionWhere = new Expression(Conjunction.OR);
		controllerActionWhere.add(new Expression(Conjunction.AND).add(new Expression("controller_path_element_name",Operator.EQ))
														.add(new Expression("action_path_element_name",Operator.EQ)));
		
		if (defaultController){
			controllerActionWhere.add(new Expression(Conjunction.AND).add(new Expression("controller_path_element_name",Operator.EQ))
														.add(new Expression("action_path_element_name",Operator.EQ)));
		}else {
			controllerActionWhere.add(new Expression(Conjunction.AND).add(new Expression("controller_path_element_name",Operator.EQ,controllerPathElementName))
					.add(new Expression("action_path_element_name",Operator.EQ)));
		}
		if (defaultController){
			controllerActionWhere.add(new Expression(Conjunction.AND).add(new Expression("controller_path_element_name",Operator.EQ))
					.add(new Expression("action_path_element_name",Operator.EQ,new BindVariable(actionPathElementName))));
		}else {
			controllerActionWhere.add(new Expression(Conjunction.AND).add(new Expression("controller_path_element_name",Operator.EQ,controllerPathElementName))
					.add(new Expression("action_path_element_name",Operator.EQ,new BindVariable(actionPathElementName))));
		}
		permissionQueryWhere.add(controllerActionWhere);

		Select userRoleQuery = new Select().from(UserRole.class).where(new Expression("user_id",Operator.EQ,new BindVariable(user.getId())));
		List<UserRole> userRoles = userRoleQuery.execute(UserRole.class);
		List<Integer> userRoleIds = new ArrayList<Integer>();
		Expression roleWhere = new Expression(Conjunction.OR);
		roleWhere.add(new Expression("role_id",Operator.EQ));
		if (!userRoles.isEmpty()){
			List<BindVariable> role_ids = new ArrayList<BindVariable>();
			for (UserRole ur:userRoles){
				role_ids.add(new BindVariable(ur.getRoleId()));
				userRoleIds.add(ur.getRoleId());
			}
			roleWhere.add(new Expression("role_id",Operator.IN,role_ids.toArray()));
		}
		permissionQueryWhere.add(roleWhere);
		
		Select permissionQuery = new Select().from(RolePermission.class);
		permissionQuery.where(permissionQueryWhere);

		List<RolePermission> permissions = permissionQuery.execute();
		
		if (selectedModel != null){ 
			for (Iterator<RolePermission> permissionIterator = permissions.iterator(); permissionIterator.hasNext() ; ){
				RolePermission permission = permissionIterator.next();
				Reader condition = permission.getConditionText();
				String sCondition = (condition == null ? null : StringUtil.read(condition));
				if (!ObjectUtil.isVoid(sCondition)){
					Expression expression = new SQLExpressionParser(modelClass).parse(sCondition);
					if (expression == null ){
						expression = new XMLExpressionParser(modelClass).parse(sCondition);
					}
					if (!expression.eval(selectedModel)) {
						permissionIterator.remove();
					}
				}
			}
			
		}

		if (permissions.isEmpty()){
			return ;
		}
		Collections.sort(permissions, rolepermissionComparator);
		
		RolePermission firstPermission = permissions.get(0);
		RolePermission currentPermissionGroup = firstPermission;

		Iterator<RolePermission> permissionIterator = permissions.iterator();
		while (permissionIterator.hasNext()){
			RolePermission effective = permissionIterator.next();
			if (permissionGroupComparator.compare(currentPermissionGroup,effective) < 0){
				if (currentPermissionGroup.getRoleId() != null ){
					userRoleIds.remove(effective.getRoleId());
				}else {
					break;
				}
				currentPermissionGroup = effective;
			}
			if (effective.getRoleId() != null && !userRoleIds.contains(effective.getRoleId())){
				//Disallowed at more granular level for this role. So Ignore this record.
				continue;
			}
			
			if (effective.isAllowed()){
				if (effective.getRoleId() != null || firstPermission.getRoleId() == null){
					return;
				}else if (!userRoleIds.isEmpty() ){
					//First role not null but effective.role is null.
					//If User has atleast one more role that is not configured as disallowed then allowed.
					return;
				}else {
					//Role level dissallowed will override.
					break;
				}
			}
		}
		throw new AccessDeniedException();
	}
	Comparator<RolePermission> permissionGroupComparator = new Comparator<RolePermission>() {
		@Override
		public int compare(RolePermission o1, RolePermission o2) {
			int ret = 0;
			if (ret == 0){
				ret = StringUtil.valueOf(o2.getControllerPathElementName()).compareTo(StringUtil.valueOf(o1.getControllerPathElementName()));
			}
			if (ret == 0){
				ret = StringUtil.valueOf(o2.getActionPathElementName()).compareTo(StringUtil.valueOf(o1.getActionPathElementName()));	
			}
			if (ret == 0 && o1.getRoleId() != null && o2.getRoleId() != null){
				ret = o1.getRoleId().compareTo(o2.getRoleId());
			}
			return ret;
		}
		
	};
	Comparator<RolePermission> rolepermissionComparator = new Comparator<RolePermission>() {

		public int compare(RolePermission o1, RolePermission o2) {
			int ret =  0; 
			if (ret == 0){
				if (o1.getRoleId() == null && o2.getRoleId() != null){
					ret = 1;
				}else if (o2.getRoleId() == null && o1.getRoleId() != null){
					ret = -1;
				}else {
					ret = 0;
				}
			}
			if (ret == 0){
				ret = permissionGroupComparator.compare(o1, o2);
			}
			if (ret == 0) {
				ret = StringUtil.valueOf(o2.getParticipation()).compareTo(StringUtil.valueOf(o1.getParticipation()));
			}
			return ret;
		}
		
	};

}
