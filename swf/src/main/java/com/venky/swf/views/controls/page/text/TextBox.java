/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.venky.swf.views.controls.page.text;

/**
 *
 * @author venky
 */
public class TextBox extends Input implements _IAutoCompleteControl{
    
    /**
	 * 
	 */
	private static final long serialVersionUID = -1921218232626387317L;

	public void setAutocompleteServiceURL(String autoCompleteServiceURL){
        setProperty("autoCompleteUrl", autoCompleteServiceURL);
    }
	public void setOnAutoCompleteSelectProcessingUrl(String url){
		setProperty("onAutoCompleteSelectUrl",url);
	}
    public TextBox(){
        super();
    }

    @Override
    protected String getInputType() {
        return "text";
    }
    
}