/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.venky.swf.routing;


import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.venky.core.util.ObjectUtil;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import com.venky.core.log.ExtendedLevel;
import com.venky.core.log.SWFLogger;
import com.venky.core.log.TimerStatistics;
import com.venky.core.log.TimerStatistics.Timer;
import com.venky.core.util.MultiException;
import com.venky.core.util.PackageUtil;
import com.venky.extension.Registry;
import com.venky.swf.db._IDatabase;
import com.venky.swf.path._IPath;
import com.venky.swf.views._IView;

/**
 *
 * @author venky
 */
public class Router extends AbstractHandler {

    protected Router() {
    	
    }
    
    private static Router router = new Router();
    public static Router instance(){
    	return router;
    }
    
    private ClassLoader loader = null; 
    public ClassLoader getLoader() {
    	synchronized (this) {
    		return loader;
		}
	}
    private void callShutdownExtensions(){
    	Registry.instance().callExtensions("com.venky.swf.routing.Router.shutdown");
    }
    public void shutDown(){
    	callShutdownExtensions();
    }
	public void setLoader(ClassLoader loader) {
		synchronized (this) {
			if (this.loader != loader) {
				shutDown();
		    	clearExtensions();
		    	if (this.loader != null){
		    		disposeDatabase();
		    	}
				this.loader = loader;
				if (loader != null){
			    	try {
			    	    ExtendedLevel.TIMER.intValue();

			    		InputStream is = this.loader.getResourceAsStream("config/logger.properties");
			    		if (is != null){
			    			LogManager.getLogManager().readConfiguration(is);
			    		}else {
			    			Config.instance().getLogger(Router.class.getName()).info("Logging not configured! using defaults");
			    		}
					} catch (Exception e1) {
						Config.instance().getLogger(Router.class.getName()).info("config/logger.properties not configured! using defaults");
					}
					_IDatabase db = getDatabase(true);
					loadExtensions();
					try {
						db.loadFactorySettings();
					}finally {
						db.close();
					}
					try {
						getPathClass();
						getExceptionViewClass();
					} catch (Exception e) {
						Config.instance().getLogger(Router.class.getName()).log(Level.SEVERE,e.getMessage(),e);
					}
				}
			}
		}
	}
	public void clearExtensions(){
		Registry.instance().clearExtensions();
	}
    public void loadExtensions(){
		for (String root : Config.instance().getExtensionPackageRoots()){
			for (URL url:Config.instance().getResourceBaseUrls()){
				for (String extnClassName : PackageUtil.getClasses(url, root.replace('.', '/'))){
					try {
						getClass(extnClassName);
					} catch (ClassNotFoundException e) {
						throw new RuntimeException(e);
					}
				}
        	}
        }
    }

	private _IPath createPath(String target){
    	try {
			Class<?> ipc = getPathClass();
			_IPath path = (_IPath)(ipc.getConstructor(String.class).newInstance(target));
			return path;
		} catch (Exception e){
			throw new RuntimeException(e);
		}
    }
	
	public Class<?> getClass(String className) throws ClassNotFoundException{
		return Class.forName(className,true,getLoader());
	}
	
	private Class<?> getPathClass() throws ClassNotFoundException{
		return getClass("com.venky.swf.path.Path");
	}
	private Class<?> getExceptionViewClass() throws ClassNotFoundException {
		return getClass("com.venky.swf.views.ExceptionView");
	}
	private Class<?> getRedirectorViewClass() throws ClassNotFoundException {
		return getClass("com.venky.swf.views.RedirectorView");
	}
	private Class<?> getDatabaseClass() throws ClassNotFoundException{
		return getClass("com.venky.swf.db.Database");
	}
	private _IView createRedirectorView(_IPath p,String url){
		try {
			Class<?> evc = getRedirectorViewClass();
			_IView ev = (_IView) evc.getConstructor(_IPath.class).newInstance(p);
			Method m = evc.getMethod("setRedirectUrl", String.class);
			m.invoke(ev, url);
			return ev;
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}
	private _IView createExceptionView(_IPath p, Throwable th){
		try {
			Class<?> evc = getExceptionViewClass();
			_IView ev = (_IView) evc.getConstructor(_IPath.class,Throwable.class).newInstance(p,th);
			return ev;
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}
	private _IDatabase getDatabase(){
		return getDatabase(false);
	}
	@SuppressWarnings("unchecked")
	private _IDatabase getDatabase(boolean migrate){
		try {
			//Database.getInstance()
			Class<_IDatabase> c = (Class<_IDatabase>)getDatabaseClass();
			_IDatabase idb = (_IDatabase)(c.getMethod("getInstance",boolean.class).invoke(c,migrate));
			return idb;
		}catch(Exception ex){
			throw new RuntimeException(ex);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void disposeDatabase(){
		try {
			//Database.getInstance()
			Class<_IDatabase> c = (Class<_IDatabase>)getDatabaseClass();
			c.getMethod("dispose").invoke(c);
			c = null;
		}catch(Exception ex){
			throw new RuntimeException(ex);
		}
	}
	
	
	
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    	SWFLogger cat = Config.instance().getLogger(getClass().getName());
    	Timer timer = cat.startTimer("handleRequest : " + target ,true);
    	try {
	        _IView view = null;
	        _IView ev = null ;	
	        
	        _IPath p = createPath(target);
	        p.setSession(request.getSession(false));
	        p.setRequest(request);
	        p.setResponse(response);
	        response.addHeader("Cache-Control","no-cache");

			String origins = Config.instance().getProperty("swf.cors.allowed.origins");
			if (!ObjectUtil.isVoid(origins)){
				response.addHeader("Access-Control-Allow-Origin" , origins );
				response.addHeader ("Access-Control-Allow-Credentials" ,"true" );
				response.addHeader ("Access-Control-Allow-Methods" ,"GET, POST, PUT, DELETE, OPTIONS");
				response.addHeader ("Access-Control-Allow-Headers" ,"Accept,Authorization,Cache-Control,Content-Type,DNT,If-Modified-Since,Keep-Alive,Origin,User-Agent,X-Requested-With,Range,ApiKey,withCredentials");

				if (p.getRequest().getMethod().equalsIgnoreCase("OPTIONS")){
					response.addHeader ("Content-Type", "text/plain charset=UTF-8");
					response.addIntHeader ("Access-Control-Max-Age" ,1728000);
					response.addIntHeader ("Content-Length" , 0);
					response.setStatus(HttpServletResponse.SC_NO_CONTENT);
					return;
				}
			}

	        Logger logger = Config.instance().getLogger(getClass().getName());
	        _IDatabase db = null ;
	        try {
	        	db = getDatabase();
	        	db.setContext(_IPath.class.getName(),p);
	            view = p.invoke();
				if (db != getDatabase()){
					// If Class Loader is reset.
					db = getDatabase();
				}
	            if (view.isBeingRedirected()){
	            	db.getCurrentTransaction().commit();
	            }
	            Timer viewWriteTimer = cat.startTimer(target+".view_write", Config.instance().isTimerAdditive());
	            try {
	            	view.write();
	            }finally{
	            	viewWriteTimer.stop();
	            }
	            db.getCurrentTransaction().commit();
	        }catch(Exception e){
	        	try {
	        		logger.log(Level.INFO, "Request failed for " + p.getTarget() + ":" + p.getRequest().getMethod(), e);
	        		db.getCurrentTransaction().rollback(e);
	        	}catch (Exception ex){
	        		logger.log(Level.INFO, "Rollback failed", ex);
	        	}
	        	if (p.isForwardedRequest()){
	        		if (e instanceof RuntimeException){
	        			throw (RuntimeException)e;
	        		}else {
	        			MultiException ex = new MultiException();
	        			ex.add(e);
	        			throw ex;
	        		}
	        	}
	        	if (p.getSession() != null){
	        		if (p.redirectOnException()){
                        p.addErrorMessage(e.getMessage());
                        Config.instance().getLogger(Router.class.getName()).log(Level.INFO, "Request failed", e);
                        if (p.getTarget().equals(p.getBackTarget())){
                            ev = createRedirectorView(p, "/dashboard");
                        }else {
                            ev = createRedirectorView(p,p.getBackTarget());
                        }
                    }else {
                        ev = createExceptionView(p, e);
                    }
	        	}else {
	        		ev = createExceptionView(p, e);
	        	}
	        	ev.write();
	        }finally {
	        	if (db != null ){
	        		db.close();
	        	}
	        	p.autoInvalidateSession();
	        }
    	}finally{
    		timer.stop();
    		TimerStatistics.dumpStatistics();
	        baseRequest.setHandled(true);
    	}
    }
	public void reset() {
		setLoader(new SWFClassLoader(getClass().getClassLoader()));
	}
}
