package com.venky.swf.exceptions;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.venky.core.util.ExceptionUtil;

public class MultiException extends RuntimeException{

	/**
	 * 
	 */
	private static final long serialVersionUID = 7536473966344832621L;

	List<Throwable> throwables = new ArrayList<Throwable>();
	public MultiException(){
		super();
	}
	
	public Throwable getContainedException(Class<?> instanceOfThisClass){
		if (instanceOfThisClass.isInstance(this)){
			return this;
		}
		for (Throwable th: throwables){
			Throwable ret = null;
			if (MultiException.class.isInstance(th)){
				ret = ((MultiException)th).getContainedException(instanceOfThisClass);
			}else {
				ret = ExceptionUtil.getEmbeddedException(th, instanceOfThisClass);
			}
			if (ret != null){
				return ret;
			}
		}
		return null;
	}
	
	public MultiException(String message){
		super(message);
	}
	
	public void add(Throwable t){
		throwables.add(ExceptionUtil.getRootCause(t));
	}
	
	public boolean isEmpty(){
		return throwables.isEmpty();
	}
	
	private String newLine(){
		return (System.getProperty("line.separator"));
	}
	@Override
	public String toString(){
		StringBuilder b = new StringBuilder();
		for (Throwable th: throwables){
			b.append(th);
			b.append(newLine());
		}
		return b.toString();
	}
	
	public void printStackTrace(PrintStream s) {
		for (Throwable th: throwables){
			th.printStackTrace(s);
			s.println();
			s.println("------------------------");
		}
	}
	public void printStackTrace(PrintWriter w) {
		for (Throwable th: throwables){
			th.printStackTrace(w);
			w.println();
			w.println("------------------------");
		}
	}
	
}
