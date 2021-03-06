/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.venky.swf.views.login;

import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.path.Path;
import com.venky.swf.routing.Config;
import com.venky.swf.views.HtmlView;
import com.venky.swf.views.controls._IControl;
import com.venky.swf.views.controls.page.Form;
import com.venky.swf.views.controls.page.Image;
import com.venky.swf.views.controls.page.Link;
import com.venky.swf.views.controls.page.LinkedImage;
import com.venky.swf.views.controls.page.buttons.Submit;
import com.venky.swf.views.controls.page.layout.Div;
import com.venky.swf.views.controls.page.layout.FluidContainer;
import com.venky.swf.views.controls.page.layout.FluidContainer.Column;
import com.venky.swf.views.controls.page.layout.Glyphicon;
import com.venky.swf.views.controls.page.layout.Span;
import com.venky.swf.views.controls.page.text.CheckBox;
import com.venky.swf.views.controls.page.text.DateBox;
import com.venky.swf.views.controls.page.text.Input;
import com.venky.swf.views.controls.page.text.Label;
import com.venky.swf.views.controls.page.text.PasswordText;
import com.venky.swf.views.controls.page.text.TextBox;

/**
 *
 * @author venky
 */
public class LoginView extends HtmlView{
	private boolean allowRegistration;
	private boolean newRegistration;

    public LoginView(Path path, boolean allowRegistration, boolean newRegistration){
        super(path);
        this.newRegistration = newRegistration;
		this.allowRegistration = allowRegistration || newRegistration;
	}

	public void addProgressiveWebAppLinks(Column column) {
		String application_name = getApplicationName();
		Image image = getLogo();
		if (image != null) {
			column.addControl(image);
		} else {
			Label appLabel = new Label(application_name);
			column.addControl(appLabel);
			appLabel.addClass("application-title");
		}
	}
	public void addExternalLoginLinks(Column column,String _redirect_to){
		if (!ObjectUtil.isVoid(Config.instance().getClientId("GOOGLE"))){
			column.addControl(new LinkedImage("/resources/images/google-icon.svg","/oid/login?SELECTED_OPEN_ID=GOOGLE" + (ObjectUtil.isVoid(_redirect_to) ? "" : "&_redirect_to=" + _redirect_to)));
		}
		if (!ObjectUtil.isVoid(Config.instance().getClientId("FACEBOOK"))){
			column.addControl(new LinkedImage("/resources/images/fb-icon.svg","/oid/login?SELECTED_OPEN_ID=FACEBOOK" + (ObjectUtil.isVoid(_redirect_to) ? "" : "&_redirect_to=" + _redirect_to)));
		}
		if (!ObjectUtil.isVoid(Config.instance().getClientId("LINKEDIN"))){
			column.addControl(new LinkedImage("/resources/images/linkedin-icon.png","/oid/login?SELECTED_OPEN_ID=LINKEDIN" + (ObjectUtil.isVoid(_redirect_to) ? "" : "&_redirect_to=" + _redirect_to)));
		}
	}
    @Override
    protected void createBody(_IControl b) {

    	String _redirect_to = StringUtil.valueOf(getPath().getFormFields().get("_redirect_to"));

    	FluidContainer loginPanel = new FluidContainer();
    	loginPanel.addClass("application-pannel");
    	b.addControl(loginPanel);
    	
    	Column applicationDescPannel = loginPanel.createRow().createColumn(3,6);
    	applicationDescPannel.addClass("text-center offset-3 col-6");
		applicationDescPannel.addClass("text-center offset-lg-5 col-lg-2");

		addProgressiveWebAppLinks(applicationDescPannel);

		Column extLinks = loginPanel.createRow().createColumn(3,6);
		extLinks.addClass("text-center");

		addExternalLoginLinks(extLinks,_redirect_to);

		Column formHolder = loginPanel.createRow().createColumn(3,6);

        Form form = new Form();
        form.setAction(getPath().controllerPath(),"login");
        form.setMethod(Form.SubmitMethod.POST);
        
        formHolder.addControl(form);
        formHolder.addControl(getStatus());

        FormGroup fg = new FormGroup();
    	fg.createTextBox(Config.instance().getProperty("Login.Name.Literal","User"), "name",false);
    	form.addControl(fg);

        fg = new FormGroup();
    	fg.createTextBox("Password", "password",true);
    	form.addControl(fg);

    	if (newRegistration){
			fg = new FormGroup();
			fg.createTextBox("Reenter Password", "password2",true);
			form.addControl(fg);
		}

        if (!ObjectUtil.isVoid(_redirect_to)){
            TextBox hidden = new TextBox();
            hidden.setVisible(false);
            hidden.setName("_redirect_to");
            hidden.setValue(_redirect_to);
            form.addControl(hidden);

        }

        fg = new FormGroup();
		Submit btn = null;
        if (allowRegistration){
        	Submit register = null;
        	if (newRegistration){
				register = fg.createSubmit("Register", 0,6);
				btn = fg.createSubmit("I'm an Existing User",0,6);
				btn.removeClass("btn-primary");
				btn.addClass("btn-link");
			}else {
				btn = fg.createSubmit("Login",0,6);
				register = fg.createSubmit("I'm a new user", 0,6);
				register.removeClass("btn-primary");
				register.addClass("btn-link");
			}
			register.setName("_REGISTER");
		}else {
			btn = fg.createSubmit("Login",3,6);
		}
		btn.setName("_LOGIN");

		form.addControl(fg);
        
    }

    private class FormGroup extends Div{
    	public FormGroup(){
    		addClass("row");
    	}

    	/**
    	 * 
    	 */
    	private static final long serialVersionUID = 4813631487870819257L;

    	
    	public Input createTextBox(String label, String fieldName , boolean password){
    		Input box = password ? new PasswordText() : new TextBox();
    		
    		box.setName(fieldName);
    		box.addClass("form-control");
    			
    		Label lbl = new Label(label);
    		lbl.setProperty("for", box.getId());
    		lbl.addClass("col-form-label");
    		lbl.addClass("col-sm-4");
    		
    		Div div = new Div();
    		div.addClass("col-sm-8");
    		div.addControl(box);
    		
    		addControl(lbl);
    		addControl(div);
    		return box;
    	}
    	
    	public CheckBox createCheckBox(String label, String fieldName) {
    		Div div = new Div();
    		div.addClass("offset-4 col-sm-4");
    		addControl(div);
    		
    		Div divcb = new Div();
    		divcb.addClass(".form-check");
    		div.addControl(divcb);
    		
    		Label lblCheckBox = new Label(label);
    		CheckBox cb = new CheckBox();
    		lblCheckBox.addControl(cb);
    		divcb.addControl(lblCheckBox);
    		
    		cb.setName(fieldName);
    		return cb;
    	}
    	public Link createLink(String label,String url, int offset, int width){
			Div div = new Div();
			div.addClass("offset-"+offset+ " col-sm-"+width);
			addControl(div);

			Link submit = new Link(url);
			div.addControl(submit);
			submit.addClass("btn btn-primary");
			submit.setText(label);
			return submit;
		}

    	public Submit createSubmit(String label, int offset, int width){
    		Div div = new Div();
    		div.addClass("offset-"+offset+ " col-sm-"+width );
    		addControl(div);
    		
    		Submit submit = new Submit(label);
    		submit.addClass("w-100");
    		div.addControl(submit);
    		return submit;
    	}
    	
    	public DateBox createDateBox(String label,String fieldName){
    		DateBox box = new DateBox();
    		
    		box.setName(fieldName);
    		box.addClass("form-control");
    			
    		Label lbl = new Label(label);
    		lbl.setProperty("for", box.getId());
    		lbl.addClass("col-form-label");
    		lbl.addClass("offset-3 col-sm-1");
    		
    		Span span = new Span();
    		span.addClass("input-group-addon");
    		span.addControl(new Glyphicon("glyphicon-calendar","Open Calendar"));
    		
    		Div div = new Div();
    		div.addClass("col-sm-4 input-group date ");
    		div.addControl(box);
    		div.addControl(span);
    		
    		
    		addControl(lbl);
    		addControl(div);
    		return box;
    	}
    }

}
