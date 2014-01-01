package com.venky.swf.db.model.io;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.Model;
import com.venky.swf.integration.FormatHelper;

public abstract class AbstractModelReader<M extends Model,T> extends ModelIO<M> implements ModelReader<M, T>{

	protected AbstractModelReader(Class<M> beanClass) {
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
	
	@Override
	public M read(T source) {
		M m = createInstance();
		FormatHelper<T> helper = FormatHelper.instance(source);
		for (String attributeName: helper.getAttributes()){
			String fieldName = getFieldName(attributeName);
			Object attrValue = helper.getAttribute(attributeName);
			if (fieldName != null) {
				Class<?> valueClass = getReflector().getFieldGetter(fieldName).getReturnType();
				Object value = Database.getJdbcTypeHelper().getTypeRef(valueClass).getTypeConverter().valueOf(attrValue); 
				if (value != null){
					getReflector().set(m, fieldName, value);
				}
			}
		}
		for (Method referredModelGetter : getReflector().getReferredModelGetters()){
			Class<? extends Model> referredModelClass = getReflector().getReferredModelClass(referredModelGetter);
			String refElementName = referredModelGetter.getName().substring("get".length());
			
			T refElement = helper.getElementAttribute(refElementName);
			if (refElement != null){ 
				Model referredModel = ModelIOFactory.getReader(referredModelClass,getFormatClass()).read(refElement);
				if (referredModel != null){
					if (referredModel.getRawRecord().isNewRecord()) {
						throw new RuntimeException("Cannot refer to data not yet pesisted." + refElement);
					}
					getReflector().set(m, getReflector().getReferenceField(referredModelGetter), referredModel.getId());
				}else {
					throw new RuntimeException(referredModelClass.getSimpleName() + " not found for passed information " + refElement.toString());
				}
			}
		}
		
		return Database.getTable(getBeanClass()).getRefreshed(m);
	}
}