package com.venky.swf.db.annotations.column.validations.processors;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import com.venky.core.util.MultiException;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;

public class DateFormatValidator extends FieldValidator<COLUMN_DEF> {

	public DateFormatValidator(String pool) {
		super(pool);
	}

	@Override
	public boolean validate(COLUMN_DEF annotation, String humanizedFieldName, String value, MultiException ex){
		if (ObjectUtil.isVoid(value)){
			return true;
		}
		if (annotation.value() == StandardDefault.CURRENT_DATE || annotation.value() == StandardDefault.CURRENT_TIMESTAMP){
			String format = annotation.args();
			if (!ObjectUtil.isVoid(format)){
				try {
					new SimpleDateFormat(format).parse(value);
				} catch (ParseException e) {
					ex.add(new FieldValidationException(humanizedFieldName + " must be in " + format + " format."));
					return false;
				}
			}
		}
		return true;
	}
	

}
