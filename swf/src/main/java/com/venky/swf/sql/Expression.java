package com.venky.swf.sql;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.venky.core.collections.SequenceSet;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.table.BindVariable;
import com.venky.swf.db.table.ModelInvocationHandler;
import com.venky.swf.db.table.Record;


public class Expression {
	String columnName = null;
	List<BindVariable> values = null ;
	Operator op = null;
	public static final int CHUNK_SIZE = 30; 
	
	public static Expression createExpression(String columnName, Operator op, Object... values){
		List<List<Object>> chunks = getValueChunks(Arrays.asList(values));
		Expression e = new Expression(Conjunction.OR);
		for (List<Object> chunk : chunks){
			e.add(new Expression(columnName,op,chunk.toArray()));
		}
		return e;
	}
	
	public static List<List<Object>> getValueChunks(List<Object> values){
		List<List<Object>> chunks = new ArrayList<List<Object>>();
		chunks.add(new ArrayList<Object>());

		for (Object bv: values){
			List<Object> aChunk = chunks.get(chunks.size()-1);
			
			if (aChunk.size() >= CHUNK_SIZE){
				aChunk = new ArrayList<Object>();
				chunks.add(aChunk);
			}
			
			if (bv != null){
				aChunk.add(bv);
			}else {
				if (!aChunk.isEmpty()){
					chunks.add(new ArrayList<Object>());
				}
				chunks.add(new ArrayList<Object>());
			}
		}
		return chunks;
	}
	
	public <T> Expression(String columnName,Operator op, @SuppressWarnings("unchecked") T... values){
		this.columnName = columnName; 
		this.op = op ;
		this.values = new SequenceSet<BindVariable>();
		
		for (int i = 0 ; i < values.length ; i ++ ){
			if (values[i] instanceof BindVariable) {
				this.values.add((BindVariable)values[i]);	
			}else {
				this.values.add(new BindVariable(values[i]));
			}
		}
		setFinalized(true);
	}
	Conjunction conjunction = null;
	public Expression(Conjunction conjunction){
		this.conjunction = conjunction;
		this.values = new ArrayList<BindVariable>();
	}

	private boolean finalized = false;
	private boolean isFinalized(){
		return finalized;
	}
	
	private void setFinalized(boolean finalized){
		this.finalized = finalized;
	}
	
	private void ensureModifiable(){
		if (isFinalized()){
			throw new ExpressionFinalizedException();
		}
	}
	
	public static class ExpressionFinalizedException extends RuntimeException {

		/**
		 * 
		 */
		private static final long serialVersionUID = -1865905730160016333L;
		
	}
	
	private List<Expression> connected = new ArrayList<Expression>();
	private Expression parent = null;
	
	public int getNumChildExpressions(){
		return connected.size();
	}
	
	
	public Expression getParent() {
		return parent;
	}

	
	public void setParent(Expression parent) {
		this.parent = parent;
	}

	public Expression add(Expression expression){
		ensureModifiable();
		expression.setParent(this);
		connected.add(expression);
		addValues(expression.getValues());
		return this;
	}
	
	private void addValues(List<BindVariable> values){
		ensureModifiable();
		this.values.addAll(values);
		if (parent != null){
			parent.addValues(values);
		}
	}
	
	private String realSQL = null;
	public String getRealSQL(){
		if (realSQL != null){
			return realSQL;
		}
		StringBuilder builder = new StringBuilder(getParameterizedSQL());
		List<BindVariable> parameters = getValues();
		
		int index = builder.indexOf("?");
		int p = 0;
		while (index >= 0) {
			BindVariable parameter = parameters.get(p);
			String pStr = StringUtil.valueOf(parameter.getValue()) ;
			if (Database.getJdbcTypeHelper().getTypeRef(parameter.getJdbcType()).isQuotedWhenUnbounded()){
				pStr = "'" + pStr + "'";
			}
			builder.replace(index, index+1, pStr);
			p+=1;
			index = builder.indexOf("?",index+pStr.length());
		}
		
		String sql = builder.toString();
		if (isFinalized()){
			realSQL = sql;
		}
		return sql;
	}
	
	private String parameterizedSQL = null ;
	public String getParameterizedSQL(){
		if (parameterizedSQL != null){
			return parameterizedSQL;
		}
		
		StringBuilder builder = new StringBuilder();
		if (conjunction == null){
			builder.append(columnName);
			builder.append(" ");
			if (values == null || values.isEmpty()){
				if (op == Operator.EQ || op == Operator.IN){
					builder.append(" IS NULL ");
				}else {
					builder.append(" IS NOT NULL ");
				}
			}else {
				builder.append(op.toString());
				if (op.isMultiValued()){
					builder.append(" ( ");
				}
				 
				for (int i = 0 ; i < values.size() ; i++){
					if (i != 0){
						//To handle In clause.
						builder.append(",");
					}
					builder.append(" ? ");
				}
				
				if (op.isMultiValued()){
					builder.append(" ) ");
				}
			}
		}else if (!connected.isEmpty()){
			boolean multipleExpressionsConnected = connected.size() > 1; 
			
			Iterator<Expression> i = connected.iterator();
			while(i.hasNext()){
				Expression expression = i.next();
				if (!expression.isEmpty()){
					if (builder.length() > 0){
						builder.append(" ");
						builder.append(conjunction);
						builder.append(" ");
					}
					builder.append(expression.getParameterizedSQL());
				}
			}
			if (builder.length() > 0 && multipleExpressionsConnected){ //avoid frivolous brackets
				builder.insert(0,"( ");
				builder.append(" )");
			}
		}
		
		String sql = builder.toString();
		if (isFinalized()){
			parameterizedSQL = sql;
		}
		return sql;
	}
	
	public List<BindVariable> getValues(){
		return Collections.unmodifiableList(values);
	}
	
	public boolean isEmpty(){
		boolean empty = false;
		if (conjunction != null){
			empty = true; 
			for (Iterator<Expression> i = connected.iterator();i.hasNext() && empty ; ){
				empty = i.next().isEmpty();
			}
		}
		
		return empty;
	}
	
	@Override
	public int hashCode(){
		setFinalized(true);
		return getRealSQL().hashCode();
	}
	
	public boolean equals(Object other){
		if (other == null){
			return false;
		}
		if (!(other instanceof Expression)){
			return false;
		}
		Expression e = (Expression) other;
		setFinalized(true);
		e.setFinalized(true);
		return getRealSQL().equals(e.getRealSQL());
	}
	
	private Object get(Object record, String columnName){
		boolean isModelProxyObject = Proxy.isProxyClass(record.getClass()) && (record instanceof Model);
		Object value = null;
		if (isModelProxyObject){
			Model m = (Model)record;
			ModelInvocationHandler h = (ModelInvocationHandler) Proxy.getInvocationHandler(m);
			String fieldName = columnName ; 
			if (!h.getReflector().getFields().contains(fieldName)) {
				fieldName = h.getReflector().getFieldName(columnName);
				if (fieldName == null){
					throw new IllegalArgumentException(columnName + " is neither a column nor field" ); 
				}
			}
			if (h.getReflector().isFieldVirtual(fieldName)){
				value = h.getReflector().get(m,fieldName);
			}else {
				value = m.getRawRecord().get(h.getReflector().getColumnDescriptor(fieldName).getName());
			}
		}else if (Record.class.isInstance(record)){
			value = ((Record)record).get(columnName);
		}else {
			throw new RuntimeException("Don't know how to get column value from object of type " + record.getClass() + " for column " + columnName);
		}
		return value;
	}
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public  boolean eval(Object record){
		if (conjunction == null){
			Object value = get(record,columnName);
			if (value == null){
				return values.isEmpty();
			}else if (values.isEmpty()){
				return false;
			}
			if (values.size() == 1){
				Object v = values.get(0).getValue();
				if (op == Operator.EQ){
					return ObjectUtil.equals(v,value);
				}else if (value instanceof Comparable){
					if (op == Operator.GE){
						return ((Comparable)value).compareTo(v) >= 0;
					}else if (op == Operator.GT){
						return ((Comparable)value).compareTo(v) > 0;
					}else if (op == Operator.LE){
						return ((Comparable)value).compareTo(v) <= 0;
					}else if (op == Operator.LT){
						return ((Comparable)value).compareTo(v) < 0;
					}else if (op == Operator.NE){
						return ((Comparable)value).compareTo(v) != 0;
					}
				}
				if (op == Operator.LK && value instanceof String && v instanceof String){
					return ((String)value).matches(((String)v).replace("%", ".*"));
				}
			}
			if (op == Operator.IN){
				if (values.contains(new BindVariable(value))){
					return true;
				}
			}
		}else if (conjunction == Conjunction.OR){
			boolean ret = connected.isEmpty();
			for (Iterator<Expression> i = connected.iterator(); !ret && i.hasNext() ;){
				Expression e = i.next();
				ret = ret || e.eval(record);
			}
			return ret;
		}else if (conjunction == Conjunction.AND){
			boolean ret = true;
			for (Iterator<Expression> i = connected.iterator(); ret && i.hasNext() ;){
				Expression e = i.next();
				ret = ret && e.eval(record);
			}
			return ret;
		}
		return false;
	}
	
}
