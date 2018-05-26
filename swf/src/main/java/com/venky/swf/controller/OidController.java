package com.venky.swf.controller;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;

import org.apache.oltu.oauth2.client.HttpClient;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.request.OAuthBearerClientRequest;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.GitHubTokenResponse;
import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.client.response.OAuthAuthzResponse;
import org.apache.oltu.oauth2.client.response.OAuthClientResponse;
import org.apache.oltu.oauth2.client.response.OAuthClientResponseFactory;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.client.response.OAuthResourceResponse;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.OAuthProviderType;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.apache.oltu.oauth2.common.utils.OAuthUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.Transaction;
import com.venky.swf.db.model.User;
import com.venky.swf.db.model.UserEmail;
import com.venky.swf.path.Path;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.HtmlView;
import com.venky.swf.views.HtmlView.StatusType;
import com.venky.swf.views.RedirectorView;
import com.venky.swf.views.View;


public class OidController extends Controller{

	public OidController(Path path) {
		super(path);
	}
	
	static class OIDProvider {
		public OIDProvider(String opendIdProvider, OAuthProviderType providerType,
				String issuer , Class< ? extends OAuthAccessTokenResponse> tokenResponseClass,String resourceUrl) {
			this.iss = issuer ; 
			this.tokenResponseClass = tokenResponseClass;
			this.resourceUrl = resourceUrl;
			this.openIdProvider = opendIdProvider ;  this.providerType = providerType; 
			this.clientId = Config.instance().getClientId(opendIdProvider); 
			this.clientSecret = Config.instance().getClientSecret(opendIdProvider) ; 
			this.redirectUrl = Config.instance().getServerBaseUrl() + "/oid/verify?SELECTED_OPEN_ID="+opendIdProvider;
		}
		String openIdProvider;
		OAuthProviderType providerType;
		String clientId;
		String clientSecret;
		String iss;
		Class<? extends OAuthAccessTokenResponse> tokenResponseClass;
		String resourceUrl;
		String redirectUrl; 
		public OAuthClientRequest createRequest(){
			try {
				return OAuthClientRequest.authorizationProvider(providerType).setClientId(clientId).setResponseType(OAuth.OAUTH_CODE)
						.setScope("email").
						setRedirectURI(redirectUrl).buildQueryMessage();
			} catch (OAuthSystemException e) {
				throw new RuntimeException(e);
			}
		}
		public String authorize(String code){
			try {
				OAuthClientRequest oauthRequest = OAuthClientRequest
				        .tokenProvider(providerType)
				        .setGrantType(GrantType.AUTHORIZATION_CODE)
				        .setClientId(clientId)
				        .setClientSecret(clientSecret)
				        .setRedirectURI(redirectUrl)
				        .setCode(code)
				        .setScope("email")
				        .buildBodyMessage();

				OAuthClient oAuthClient = new OAuthClient(new OidHttpClient());

		        OAuthAccessTokenResponse oAuthResponse = oAuthClient.accessToken(oauthRequest, tokenResponseClass);
		        
		        if (ObjectUtil.isVoid(resourceUrl)){
					return extractEmail(oAuthResponse);
		        }else {
		        	String accessToken = oAuthResponse.getAccessToken();
			        Long expiresIn = oAuthResponse.getExpiresIn();
			    	OAuthClientRequest bearerClientRequest = new OAuthBearerClientRequest(resourceUrl)
					         .setAccessToken(accessToken).buildQueryMessage();
					 
					OAuthResourceResponse resourceResponse = oAuthClient.resource(bearerClientRequest, OAuth.HttpMethod.GET, OAuthResourceResponse.class);
					
					return extractEmail(resourceResponse);
		        }
			} catch (Exception e) {
				throw new RuntimeException(e);
			}		
		}
		public String extractEmail(OAuthResourceResponse oAuthResponse) throws Exception{
			JSONObject body = (JSONObject) new JSONParser().parse(oAuthResponse.getBody());
			String email =  (String) body.get("email");
			return email;
		}
		public String extractEmail(OAuthAccessTokenResponse oAuthResponse) throws Exception{
	        if (oAuthResponse instanceof OAuthJSONAccessTokenResponse){
		        String idToken = oAuthResponse.getParam("id_token");
		        StringTokenizer tk = new StringTokenizer(idToken,".");
		        String headerBuf = new String(Base64.getDecoder().decode(tk.nextToken()));
		        String bodyBuf = new String(Base64.getDecoder().decode(tk.nextToken()));
		        JSONObject header = (JSONObject) new JSONParser().parse(headerBuf);
		        JSONObject body = (JSONObject) new JSONParser().parse(bodyBuf);
		        
		        String emailId = (String) body.get("email");
		        String[] issuers = new String[] {iss, "http://" + iss , "https://" + iss };
		        
		        if (body.get("aud").equals(clientId) && Arrays.asList(issuers).contains(body.get("iss"))) {
		        	  return emailId;
		    	}
			}
	        throw new RuntimeException("OAuth Failed");

		}
	 
	}
	private static Map<String,OIDProvider> oidproviderMap = new HashMap<>();
	static { 
		oidproviderMap.put("GOOGLE", new OIDProvider("GOOGLE",OAuthProviderType.GOOGLE,"accounts.google.com",
				OAuthJSONAccessTokenResponse.class,""));
		oidproviderMap.put("FACEBOOK", new OIDProvider("FACEBOOK",OAuthProviderType.FACEBOOK,"",
				GitHubTokenResponse.class,"https://graph.facebook.com/me?fields=email,name"));

	}
	
	@SuppressWarnings("rawtypes")
	protected View authenticate() {
		String selectedOpenId = getPath().getRequest().getParameter("SELECTED_OPEN_ID");
		
		if (ObjectUtil.isVoid(selectedOpenId)){
			HtmlView lv = createLoginView();
			lv.setStatus(StatusType.ERROR, "Open id provider not specified");
			return lv;
		}
		
		try {
			OAuthClientRequest request = oidproviderMap.get(selectedOpenId).createRequest();
			RedirectorView ret = new RedirectorView(getPath());
			ret.setRedirectUrl(request.getLocationUri());
			return ret;
		} catch (Exception e) {
			return createLoginView(StatusType.ERROR,e.getMessage());
		} 
	}
	
	
	@SuppressWarnings("rawtypes")
	@RequireLogin(false)
	public View verify() throws OAuthProblemException, OAuthSystemException, ParseException{
		HttpServletRequest request = getPath().getRequest();
		OAuthAuthzResponse oar = OAuthAuthzResponse.oauthCodeAuthzResponse(request);
		String code = oar.getCode();
		
		String selectedOpenId = getPath().getRequest().getParameter("SELECTED_OPEN_ID");
		OIDProvider provider = oidproviderMap.get(selectedOpenId);

		try {
	        String email = provider.authorize(code);
    		User u = null;
			Select select = new Select().from(UserEmail.class);
			select.where(new Expression(select.getPool(),"email",Operator.EQ, email));
			List<UserEmail> oids = select.execute(UserEmail.class);
			int numOids = oids.size();
			
			if (numOids > 0) {
    			SortedSet<Long> numUsers = new TreeSet<Long>();
    			for (UserEmail oid: oids){
    				numUsers.add(oid.getUserId());
    			}
    			if (numUsers.size() > 1) {
    				return createLoginView(StatusType.ERROR, "Multiple users associated with same email id");
    			}
				u = Database.getTable(User.class).get(numUsers.first());
			}
			if (u == null){
    			Transaction txn = Database.getInstance().getTransactionManager().createTransaction();
				u = Database.getTable(User.class).newRecord();
    			u.setName(email);
    			u.setPassword(null);
    			u.save();
    			UserEmail oid = Database.getTable(UserEmail.class).newRecord();
    			oid.setUserId(u.getId());
    			oid.setEmail(email);
    			oid.save();
    			txn.commit();
    		}
    		getPath().createUserSession(u, false);
            
			return new RedirectorView(getPath(), loginSuccessful());
		} catch (Exception e) {
			return createLoginView(StatusType.ERROR, e.getMessage());
		}
	}
	
	public static class OidHttpClient implements HttpClient {

	    public OidHttpClient() {
	    }

	    public <T extends OAuthClientResponse> T execute(OAuthClientRequest request, Map<String, String> headers,
	                                                     String requestMethod, Class<T> responseClass)
	            throws OAuthSystemException, OAuthProblemException {

	        InputStream responseBody = null;
	        URLConnection c;
	        Map<String, List<String>> responseHeaders = new HashMap<String, List<String>>();
	        int responseCode;
	        try {
	            URL url = new URL(request.getLocationUri());

	            c = url.openConnection();
	            c.setConnectTimeout(5000);
	            c.setReadTimeout(5000);
	            responseCode = -1;
	            if (c instanceof HttpURLConnection) {
	                HttpURLConnection httpURLConnection = (HttpURLConnection) c;

	                if (headers != null && !headers.isEmpty()) {
	                    for (Map.Entry<String, String> header : headers.entrySet()) {
	                        httpURLConnection.addRequestProperty(header.getKey(), header.getValue());
	                    }
	                }

	                if (request.getHeaders() != null) {
	                    for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
	                        httpURLConnection.addRequestProperty(header.getKey(), header.getValue());
	                    }
	                }

	                if (OAuthUtils.isEmpty(requestMethod)) {
	                    httpURLConnection.setRequestMethod(OAuth.HttpMethod.GET);
	                } else {
	                    httpURLConnection.setRequestMethod(requestMethod);
	                    setRequestBody(request, requestMethod, httpURLConnection);
	                }
	                
	                httpURLConnection.connect();

	                InputStream inputStream;
	                responseCode = httpURLConnection.getResponseCode();
	                if (responseCode == SC_BAD_REQUEST || responseCode == SC_UNAUTHORIZED) {
	                    inputStream = httpURLConnection.getErrorStream();
	                } else {
	                    inputStream = httpURLConnection.getInputStream();
	                }

	                responseHeaders = httpURLConnection.getHeaderFields();
	                responseBody = inputStream;
	            }
	        } catch (IOException e) {
	            throw new OAuthSystemException(e);
	        }

	        return OAuthClientResponseFactory
	                .createCustomResponse(responseBody, c.getContentType(), responseCode, responseHeaders, responseClass);
	    }

	    private void setRequestBody(OAuthClientRequest request, String requestMethod, HttpURLConnection httpURLConnection)
	            throws IOException {
	        String requestBody = request.getBody();
	        if (OAuthUtils.isEmpty(requestBody)) {
	            return;
	        }

	        if (OAuth.HttpMethod.POST.equals(requestMethod) || OAuth.HttpMethod.PUT.equals(requestMethod)) {
	            httpURLConnection.setDoOutput(true);
	            OutputStream ost = httpURLConnection.getOutputStream();
	            PrintWriter pw = new PrintWriter(ost);
	            pw.print(requestBody);
	            pw.flush();
	            pw.close();
	        }
	    }

	    @Override
	    public void shutdown() {
	        // Nothing to do here
	    }

	}

}
