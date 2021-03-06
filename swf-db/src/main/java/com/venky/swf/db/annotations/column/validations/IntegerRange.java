package com.venky.swf.db.annotations.column.validations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)

public @interface IntegerRange {
	public int min() default Integer.MIN_VALUE;
	public int max() default Integer.MAX_VALUE;
} 
