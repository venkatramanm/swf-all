package com.venky.swf.db.annotations.column;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UNIQUE_KEY {
	public String value() default "K1" ;
	public boolean allowMultipleRecordsWithNull() default true;
	public boolean exportable() default true;
}
