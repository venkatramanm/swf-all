package com.venky.swf.db.model.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.venky.cache.Cache;
import com.venky.core.log.SWFLogger;
import com.venky.core.log.TimerStatistics.Timer;
import com.venky.core.log.TimerUtils;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.routing.Config;

public abstract class AbstractModelWriter<M extends Model,T> extends ModelIO<M> implements ModelWriter<M, T>{

	protected AbstractModelWriter(Class<M> beanClass) {
		super(beanClass);
	}
	@SuppressWarnings("unchecked")
	public Class<T> getFormatClass(){
		ParameterizedType pt = (ParameterizedType)this.getClass().getGenericSuperclass();
		return (Class<T>) pt.getActualTypeArguments()[1];
	}
	
	public MimeType getMimeType(){
		return FormatHelper.getMimeType(getFormatClass());
	}
	private List<String> getFields(List<String> includeFields){
		return getFields(getReflector(),includeFields);
	}
	private static <R extends Model> List<String> getFields(ModelReflector<R> reflector, List<String> includeFields) {
		List<String> fields = includeFields;
		if (fields == null){
			fields = reflector.getFields();
			Iterator<String> fi = fields.iterator();
			while (fi.hasNext()){
				String field = fi.next();
				if (reflector.isFieldHidden(field) && !"ID".equalsIgnoreCase(field)){
					fi.remove();
				}
			}
		}
		return fields;
	}
	

	public void write(List<M> records, OutputStream os,List<String> fields) throws IOException {
		Map<Class<? extends Model> , List<String>> mapFields = new HashMap<Class<? extends Model>, List<String>>();
		Set<Class<? extends Model>> parentsWritten = new HashSet<>();
		write (records,os,fields,parentsWritten,mapFields);
	}
	public void write (List<M> records,OutputStream os, List<String> fields, Set<Class<?extends Model>> parentsAlreadyConsidered,
					   Map<Class<? extends Model>,List<String>> templateFields) throws IOException {
		write(records,os,fields,parentsAlreadyConsidered,getChildrenToConsider(),templateFields);
	}

	public void write (List<M> records,OutputStream os, List<String> fields, Set<Class<? extends Model>> parentsAlreadyConsidered ,
					   Map<Class<? extends Model>,List<Class<? extends Model>>> childrenToBeConsidered,
					   Map<Class<? extends Model>,List<String>> templateFields) throws IOException {
		FormatHelper<T> helper  = FormatHelper.instance(getMimeType(),StringUtil.pluralize(getBeanClass().getSimpleName()),true);
		for (M record: records){
			T childElement = helper.createChildElement(getBeanClass().getSimpleName());
			write(record,childElement,fields,parentsAlreadyConsidered, childrenToBeConsidered, templateFields);
		}
		os.write(helper.toString().getBytes());
	}
	public void write(M record,T into, List<String> fields){
		write(record,into,fields,new HashSet<>(), new HashMap<>());
	}
	private boolean isParentIgnored(Class<? extends Model> parent, Set<Class<? extends Model>> ignoredParents) {
		for (Class<? extends Model> ignoredParent : ignoredParents) {
			if (ObjectUtil.equals(ignoredParent.getSimpleName(),parent.getSimpleName())) {
				return true;
			}
		}
		return false;
	}
	private final SWFLogger cat = Config.instance().getLogger(getClass().getName());
	public Map<Class<? extends Model> , List<Class<? extends Model>>> getChildrenToConsider(){
		Map<Class<? extends Model>,List<Class<? extends Model>>> considerChildren = new Cache<Class<? extends Model>, List<Class<? extends Model>>>() {
			@Override
			protected List<Class<? extends Model>> getValue(Class<? extends Model> aClass) {
				return new ArrayList<>();
			}
		};
		for (Class<? extends Model> childModelClass : getReflector().getChildModels()){
			considerChildren.get(getBeanClass()).add(childModelClass);
		}
		return  considerChildren;

	}
	public void write(M record,T into, List<String> fields, Set<Class<? extends Model>> parentsAlreadyConsidered , Map<Class<? extends Model>,List<String>> templateFields) {
		//Consider first level children.
		write(record,into,fields,parentsAlreadyConsidered,getChildrenToConsider(),templateFields);
	}
	public void write(M record,T into, List<String> fields, Set<Class<? extends Model>> parentsAlreadyConsidered ,
					  Map<Class<? extends Model>, List<Class<? extends  Model>>> considerChilden,
					  Map<Class<? extends Model>, List<String>> templateFields) {
		FormatHelper<T> formatHelper = FormatHelper.instance(into);
		ModelReflector<M> ref = getReflector();
		for (String field: getFields(fields)){
			Object value = TimerUtils.time(cat,"ref.get", ()-> ref.get(record, field));
			if (value == null){
				continue;
			}
			Method fieldGetter = TimerUtils.time(cat,"getFieldGetter" , () ->ref.getFieldGetter(field));
			Method referredModelGetter = TimerUtils.time(cat,"getReferredModelGetterFor" , ()->ref.getReferredModelGetterFor(fieldGetter));

			if (referredModelGetter != null){
				Class<? extends Model> aParent = ref.getReferredModelClass(referredModelGetter);
				if (!isParentIgnored(aParent,parentsAlreadyConsidered)) {
					String refElementName = referredModelGetter.getName().substring("get".length());
					T refElement = formatHelper.createElementAttribute(refElementName);
					parentsAlreadyConsidered.add(aParent);
					try {
						write(aParent, ((Number) value).intValue(), refElement, parentsAlreadyConsidered, considerChilden,templateFields);
					}finally {
						parentsAlreadyConsidered.remove(aParent);
					}
				}
			}else {
				String attributeName = TimerUtils.time(cat,"getAttributeName()" , ()->getAttributeName(field));
				String sValue = TimerUtils.time(cat, "toStringISO" ,
						() -> Database.getJdbcTypeHelper(getReflector().getPool()).getTypeRef(fieldGetter.getReturnType()).getTypeConverter().toStringISO(value));

				if (InputStream.class.isAssignableFrom(fieldGetter.getReturnType())) {
				    formatHelper.setElementAttribute(attributeName,sValue);
                }else {
                    TimerUtils.time(cat,"setAttribute" , ()-> {
						formatHelper.setAttribute(attributeName, sValue);
						return true;
					});
                }
			}
		}

		TimerUtils.time(cat,"Writing all Children Objects",()->{
			if (!templateFields.isEmpty()){
				parentsAlreadyConsidered.add(ref.getModelClass());
				try {
					List<Method> childGetters = ref.getChildGetters();
					for (Method childGetter : childGetters) {
						write(formatHelper,record,childGetter,parentsAlreadyConsidered,considerChilden,templateFields);
					}
				}finally {
					parentsAlreadyConsidered.remove(ref.getModelClass());
				}
			}
			return  true;
		});
	}
	private <R extends  Model> boolean containsChild(Class<R> childModelClass, Collection<Class<? extends  Model>> considerChilden){
		for (Class<? extends Model> possibleChildClass : ModelReflector.instance(childModelClass).getModelClasses()){
			if (considerChilden.contains(possibleChildClass)){
				return true;
			}
		}
		return false;
	}
	private <R extends Model> void write(FormatHelper<T> formatHelper, M record, Method childGetter, Set<Class<? extends Model>> parentsWritten ,
										 Map<Class<? extends Model>, List<Class<? extends  Model>>> considerChilden,
										 Map<Class<? extends Model>, List<String>> templateFields){
		@SuppressWarnings("unchecked")
		Class<R> childModelClass = (Class<R>) getReflector().getChildModelClass(childGetter);
		if (!containsChild(childModelClass,considerChilden.get(getBeanClass()))){
			return;
		}

		if (!containsChild(childModelClass,templateFields.keySet())){
			return;
		}
		
		ModelWriter<R,T> childWriter = ModelIOFactory.getWriter(childModelClass, getFormatClass());
		try {
			@SuppressWarnings("unchecked")
			List<R> children = (List<R>)childGetter.invoke(record);
			List<String> fields = templateFields.remove(childModelClass);
			try {
				for (R child : children) {
					T childElement = formatHelper.createChildElement(childModelClass.getSimpleName());
					childWriter.write(child, childElement, fields, parentsWritten, templateFields);
				}
			}finally {
				templateFields.put(childModelClass, fields);
			}
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
		
	}
	
	private <R extends Model> void write(Class<R> referredModelClass, long id, T referredModelElement,  Set<Class<? extends Model>> parentsAlreadyConsidered,
										 Map<Class<? extends Model>,List<Class<? extends Model>>> considerChildren,
										 Map<Class<? extends Model>, List<String>> templateFields){
		Class<T> formatClass = getFormatClass();
		ModelWriter<R,T> writer = ModelIOFactory.getWriter(referredModelClass,formatClass);
		R referredModel = Database.getTable(referredModelClass).get(id);
		if (referredModel == null){
			return;
		}
		ModelReflector<R> referredModelReflector = ModelReflector.instance(referredModelClass);

		List<String> parentFieldsToAdd = referredModelReflector.getUniqueFields();
		for (Iterator<String> fi = parentFieldsToAdd.iterator(); fi.hasNext();){
			String f = fi.next();
			if (referredModelReflector.isFieldHidden(f)){
				fi.remove();
			}
		}
		parentFieldsToAdd.add("ID");

		if (templateFields != null){
			for (Class<? extends  Model> clazz : referredModelReflector.getModelClasses()){
				if (templateFields.containsKey(clazz)) {
					parentFieldsToAdd.addAll(getFields(referredModelReflector,templateFields.get(clazz)));
				}
			}
        }
        List<Class<? extends Model>> childModels = referredModelReflector.getChildModels();
		Map<Class<? extends  Model>, List<String>> newTemplateFields = null;
		if (templateFields != null){
			newTemplateFields = new HashMap<>(templateFields);
			for (Class<? extends  Model> childModelClass : childModels){
				newTemplateFields.remove(childModelClass);
			}
		}

		writer.write(referredModel , referredModelElement, parentFieldsToAdd, parentsAlreadyConsidered, considerChildren, newTemplateFields);
	}
	
}
