package com.venky.swf.db.annotations.column.defaulting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)

public @interface CLONING_PROTECT {
	public boolean value()  default true ;
}
