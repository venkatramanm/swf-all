package com.venky.swf.sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.venky.swf.db.table.Record;

public class DataManupulationStatement extends SqlStatement{
	protected Level level = Level.FINE;
	public DataManupulationStatement(){
		
	}
	
	public void setLevel(Level level){
		this.level = level;
	}

	public int executeUpdate(){
        return executeUpdate(null);
    }
    public int executeUpdate(Record generatedKeyValues,String... generatedKeyColumns){ 
		Logger.getLogger(getClass().getName()).log(level, "Executing {0}", getRealSQL());
        
        PreparedStatement st = null;
        try {
            st = prepare(generatedKeyColumns);
            int ret = st.executeUpdate();
            ResultSet rs = st.getGeneratedKeys(); 
            if (generatedKeyValues != null && rs != null && rs.next()){
                generatedKeyValues.load(rs);
            }
            return ret;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (st != null){
                try {
                    if (!st.isClosed()){
                        st.close();
                    }
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        
    }

}
