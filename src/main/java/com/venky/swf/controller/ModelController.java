/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.venky.swf.controller;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper.TypeRef;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.db.table.BindVariable;
import com.venky.swf.exceptions.AccessDeniedException;
import com.venky.swf.routing.Path;
import com.venky.swf.routing.Path.ModelInfo;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.RedirectorView;
import com.venky.swf.views.View;
import com.venky.swf.views.model.ModelEditView;
import com.venky.swf.views.model.ModelListView;
import com.venky.swf.views.model.ModelShowView;

/**
 *
 * @author venky
 */
public class ModelController<M extends Model> extends Controller {

    private Class<M> modelClass;
    private ModelReflector<M> reflector ;
    public ModelController(Path path) {
        super(path);
        modelClass = getPath().getModelClass();
    	reflector = ModelReflector.instance(modelClass);
        
    }
    public Expression getWhereClause(){
    	Expression where = new Expression(Conjunction.OR);
		List<Method> parentGetters = reflector.getParentModelGetters();
		List<ModelInfo> modelElements =getPath().getModelElements();

		//TODO Correct parent id identification based on action. 
		for (Iterator<ModelInfo> miIter = modelElements.iterator() ; miIter.hasNext() ;){ // The last model is self.
    		ModelInfo mi = miIter.next();
    		if(!miIter.hasNext()){
    			//last model is self.
    			break;
    		}
    		
    		Expression parentWhere = new Expression(Conjunction.OR);
    		
    		for (Method parentGetter: parentGetters){
    	    	Class<?> parentModelClass = parentGetter.getReturnType();
        		if (parentModelClass == mi.getModelClass()){
        	    	String parentIdFieldName =  StringUtil.underscorize(parentGetter.getName().substring(3) +"Id");
        	    	String parentIdColumnName = reflector.getColumnDescriptor(parentIdFieldName).getName();
        	    	parentWhere.add(new Expression(parentIdColumnName,Operator.EQ,new BindVariable(mi.getId())));
        		}
    		}
    		
    		if (parentWhere.getParameterizedSQL().length() > 0){
    			where.add(parentWhere);
    		}
    	}
		
		Expression dsw = getDataSecurityWhere();
		if (dsw.getParameterizedSQL().length()> 0){
			where.add(dsw); 
		}
    	return where;
		    	
    }
    public Expression getDataSecurityWhere(){
    	return getSessionUser().getDataSecurityWhereClause(modelClass);
    }
    @Override
    public View index() {
        Select q = new Select().from(Database.getInstance().getTable(modelClass).getTableName());
        List<M> records = q.where(getWhereClause()).execute();
        return dashboard(new ModelListView<M>(getPath(), modelClass, null, records));
    }
    

    public View show(int id) {
        M record = Database.getInstance().getTable(modelClass).get(id);
        if (record.isAccessibleBy(getSessionUser())){
            return dashboard(new ModelShowView<M>(getPath(), modelClass, null, record));
        }else {
        	throw new AccessDeniedException();
        }
    }

    public View edit(int id) {
        M record = Database.getInstance().getTable(modelClass).get(id);
        if (record.isAccessibleBy(getSessionUser())){
            return dashboard(new ModelEditView<M>(getPath(), modelClass, null, record));
        }else {
        	throw new AccessDeniedException();
        }
    }

    public View blank() {
        M record = Database.getInstance().getTable(modelClass).newRecord();
		List<ModelInfo> modelElements =getPath().getModelElements();
		for (Iterator<ModelInfo> miIter = modelElements.iterator() ; miIter.hasNext() ;){
    		ModelInfo mi = miIter.next();
    		if(!miIter.hasNext()){
    			//last model is self.
    			break;
    		}
    		for (Method parentGetter: reflector.getParentModelGetters()){
    	    	Class<? extends Model> parentModelClass = (Class<? extends Model>)parentGetter.getReturnType();
    	    	
        		if (parentModelClass == mi.getModelClass()){
        	    	String parentIdFieldName =  reflector.getReferredModelIdFieldName(parentGetter);
        	    	Method parentIdSetter =  reflector.getFieldSetter(parentIdFieldName);
        	    	try {
            	    	Model parent = Database.getInstance().getTable(parentModelClass).get(mi.getId());
            	    	if (parent.isAccessibleBy(getSessionUser())){
            	    		parentIdSetter.invoke(record, mi.getId());
            	    	}
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
        		}
    		}
		}

		ModelEditView<M> mev = new ModelEditView<M>(getPath(), modelClass, null, record);
        mev.getIncludedFields().remove("ID");
        return dashboard(mev);
    }

    public View destroy(int id){ 
        M record = Database.getInstance().getTable(modelClass).get(id);
        if (record != null){
            if (record.isAccessibleBy(getSessionUser())){
                record.destroy();
            }else {
            	throw new AccessDeniedException();
            }
        }
        return back();
    }
    
    public RedirectorView back(){
    	RedirectorView v = new RedirectorView(getPath());
    	v.setRedirectUrl(getPath().getBackTarget());
    	return v;
    }
    
    private boolean isNew(){ 
        return ObjectUtil.isVoid(getPath().getRequest().getParameter("ID"));
    }
    private void persistInDB(){
        HttpServletRequest request = getPath().getRequest();
        if (!request.getMethod().equalsIgnoreCase("POST")) {
            throw new RuntimeException("Cannot call save in any other method other than POST");
        }
        
        Map<String,Object> formFields = new HashMap<String, Object>();
        boolean isMultiPart = ServletFileUpload.isMultipartContent(request);
        if (isMultiPart){
        	FileItemFactory factory = new DiskFileItemFactory(1024*1024*128, new File(System.getProperty("java.io.tmpdir")));
        	ServletFileUpload fu = new ServletFileUpload(factory);
        	try {
				List<FileItem> fis = fu.parseRequest(request);
				for (FileItem fi:fis){
					if (fi.isFormField()){
						formFields.put(fi.getFieldName(), fi.getString());
					}else {
						formFields.put(fi.getFieldName(), fi.getInputStream());
					}
				}
			} catch (FileUploadException e1) {
				throw new RuntimeException(e1);
			} catch (IOException e1){
				throw new RuntimeException(e1);
			}
        }else {
        	Enumeration<String> parameterNames = request.getParameterNames();
        	while (parameterNames.hasMoreElements()){
        		String name =parameterNames.nextElement();
            	formFields.put(name,request.getParameter(name));
        	}
        }
        String id = (String)formFields.get("ID");
        String lockId = (String)formFields.get("LOCK_ID");
        M record = null;
        if (ObjectUtil.isVoid(id)) {
            record = Database.getInstance().getTable(modelClass).newRecord();
        } else {
            record = Database.getInstance().getTable(modelClass).get(Integer.valueOf(id));
            if (!ObjectUtil.isVoid(lockId)) {
                if (record.getLockId() != Long.parseLong(lockId)) {
                    throw new RuntimeException("Stale record update prevented. Please reload and retry!");
                }
            }
            if (!record.isAccessibleBy(getSessionUser())){
            	throw new AccessDeniedException();
            }
        }

        ModelReflector<M> reflector = ModelReflector.instance(modelClass);
        List<String> fields = reflector.getFields();
        fields.remove("ID");
        fields.remove("LOCK_ID");
        fields.remove("UPDATED_AT");
        fields.remove("UPDATER_USER_ID");
        
        Iterator<String> e = formFields.keySet().iterator();
        while (e.hasNext()) {
            String name = e.next();
            String fieldName = fields.contains(name) ? name : null;

            if (fieldName != null) {
                Object value = formFields.get(fieldName);
                Method getter = reflector.getFieldGetter(fieldName);
                Method setter = reflector.getFieldSetter(fieldName);

                TypeRef<?> typeRef = Database.getInstance().getJdbcTypeHelper().getTypeRef(getter.getReturnType());

                try {
                	if (ObjectUtil.isVoid(value) && reflector.getColumnDescriptor(getter).isNullable()){
                        setter.invoke(record, getter.getReturnType().cast(null));
            		}else {
                        setter.invoke(record, typeRef.getTypeConverter().valueOf(value));
                	}
                } catch (Exception e1) {
                    throw new RuntimeException(e1);
                }
            }

        }
        if (isNew()){
        	record.setCreatorUserId(getSessionUser().getId());
        	record.setCreatedAt(null);
    	}
        record.setUpdaterUserId(getSessionUser().getId());
        record.setUpdatedAt(null);
        if (record.isAccessibleBy(getSessionUser())){
            record.save();
        }else {
        	throw new AccessDeniedException();
        }
    }
    
    public View save() {
    	persistInDB();
        return back();
    }

    public View autocomplete(String value) {
    	ModelReflector<M> reflector = ModelReflector.instance(modelClass);
        return super.autocomplete(modelClass,getWhereClause(), reflector.getDescriptionColumn(), value);
    }
	public ModelReflector<M> getReflector() {
		return reflector;
	}
    
    
    
}
