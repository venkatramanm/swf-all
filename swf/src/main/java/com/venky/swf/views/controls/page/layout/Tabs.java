package com.venky.swf.views.controls.page.layout;

import com.venky.swf.views.controls.page.Link;
import com.venky.swf.views.controls.page.UnorderedList;
import com.venky.swf.views.controls.page.UnorderedList.ListItem;

public class Tabs extends Div{

	private static final long serialVersionUID = -336371552638708499L;
	private UnorderedList ul = new UnorderedList();
	private Div content = new Div();
	public Tabs(){
		super();
		addControl(ul);
		addControl(content);
		ul.addClass("nav nav-tabs");
		content.addClass("tab-content");
	}
	public void addSection(Div tabpane,String tabName,boolean makeActive){
		tabpane.addClass("tab-pane fade");
		Link link = new Link("#"+tabpane.getProperty("id"));
		link.setText(tabName);
		link.setProperty("data-toggle","tab");
		
		ListItem li = ul.createListItem();
		li.addControl(link);
		content.addControl(tabpane);
		
		if (makeActive){
			li.addClass("active");
			tabpane.addClass("in active");
		}
	}
	

}
