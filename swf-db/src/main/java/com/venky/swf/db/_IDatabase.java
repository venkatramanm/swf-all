package com.venky.swf.db;

import java.sql.SQLException;

import com.venky.swf.util._ICloseable;

public interface _IDatabase extends  _ICloseable{
	public void open(Object user);
	public _ITransaction getCurrentTransaction() throws SQLException;
	public void loadFactorySettings();
	public interface _ITransaction {
		public void commit() ;
		public void rollback(Throwable th) ;
		public <A> A getAttribute(String string); 
	}
}