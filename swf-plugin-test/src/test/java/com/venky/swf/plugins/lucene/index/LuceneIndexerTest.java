package com.venky.swf.plugins.lucene.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.search.Query;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.venky.swf.db.Database;
import com.venky.swf.plugins.lucene.index.common.ResultCollector;
import com.venky.swf.routing.Router;
import com.venky.swf.test.db.model.Sample;

public class LuceneIndexerTest {
	@Before
	public void setUp(){ 
		Router.instance().setLoader(getClass().getClassLoader());
		truncateSample();
		createSample("Jack");
		createSample("Venky");
		commitAndWaitLucene();
	}
	private int truncateSample(){
		return Database.getTable(Sample.class).truncate();
	}
	private void commitAndWaitLucene(){
		Database.getInstance().getCurrentTransaction().commit();
		sleep(5000);
	}
	private void sleep(long time){
		try {
			Thread.sleep(time);
		} catch (InterruptedException e) {
			//Simple sleep.
		}
	}
	
	@After
	public void tearDown(){
		truncateSample();
		commitAndWaitLucene();
	}

	private void createSample(String name){
		Sample sample = Database.getTable(Sample.class).newRecord();
		sample.setName(name);
		sample.save();
	}

	@Test
	public void test() {
		Database db = null ;
		try {
			db = Database.getInstance();
			db.open(null);
			
			LuceneIndexer indexer = LuceneIndexer.instance(Sample.class);
			Query q = indexer.constructQuery("j*");
			indexer.fire(q, 10, new ResultCollector() {
				int i =1;
				public void found(Document doc) {
					System.out.println("Document #"+ i);
					for (Fieldable f:doc.getFields()){
						System.out.println(f.name() +":" + f.stringValue());
					}
					
				}
			});
			Database.getInstance().getCurrentTransaction().commit();
		}finally{
			Database.getInstance().close();
		}
	}

}
