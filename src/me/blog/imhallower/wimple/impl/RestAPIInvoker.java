package me.blog.imhallower.wimple.impl;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import me.blog.imhallower.wimple.impl.util.AndroidServiceIteratorProvider;
import me.blog.imhallower.wimple.impl.util.SSLClientHelper;
import me.blog.imhallower.wimple.impl.util.Utils;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.util.Log;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.spi.service.ServiceFinder;

public class RestAPIInvoker {

	private static final String LOG_TAG = "RestAPIInvoker";
	private final IWimpleImpl wimple;
	
	private enum HTTP_METHOD { GET, POST, PUT, DELETE }

	public RestAPIInvoker(IWimpleImpl wimpleImpl){
		this.wimple = wimpleImpl;
	}	
	
	public JSONObject invokeGET(String path){
		return invokeRESTAPI(HTTP_METHOD.GET, path, "");
	}
	
	public JSONObject invokeGET(String path, String params){
		return invokeRESTAPI(HTTP_METHOD.GET, path, params);
	}
	
	public JSONObject invokePOST(String path, String params){
		return invokeRESTAPI(HTTP_METHOD.POST, path, params);
	}

	
	private JSONObject invokeRESTAPI(HTTP_METHOD method, String path, String params){		

		Client client = null;
		WebResource webResource = null;
		JSONObject object = null;

		try{
			ServiceFinder.setIteratorProvider(new AndroidServiceIteratorProvider<Object>());

			Log.d(LOG_TAG, "Invoke REST API, " + method.toString() + " , Path=" + path);
			client = SSLClientHelper.createClient();
			webResource = client.resource(wimple.getServicehost() + path);
			ClientResponse response;

			WebResource.Builder wb = webResource.type("application/x-www-form-urlencoded");

			if(wimple.isAuthed()){
				wb = wb.header("X-API-KEY", getXAPIKey());
			}

			switch(method){

			case GET :
				response = wb.get(ClientResponse.class);
				break;


			case POST :				
				response = wb.post(ClientResponse.class, params);				
				break;

			case DELETE :
			case PUT:
			default :
				throw new Exception("Not supported HTTP Method");
			}

			String output = response.getEntity(String.class);
			Log.d(LOG_TAG, "result -------------------------------------------------");
			Log.d(LOG_TAG, output.toString());
			Log.d(LOG_TAG, "result -------------------------------------------------");

			if (response.getStatus() != 200 &&
					response.getStatus() != 201) {

				throw new RuntimeException("Failed : HTTP error code : "
						+ response.getStatus());
			}

			JSONParser parser = new JSONParser();
			object = (JSONObject) parser.parse(output);

			webResource = null;
			client.destroy();
			client = null;

		} catch (Exception e) {

			e.printStackTrace();

		} finally {

			webResource = null;
			if(null != client){
				client.destroy();	
			}
			client = null;
		}

		return object;
	}

	public Map<String, String> invokeRESTAPIForMap(String path, String params){

		Map<String, String> list = new HashMap<String, String>();

		JSONObject object = invokePOST(path, params);

		if(null == object){
			return list;
		}

		for(Object key : object.keySet()){
			String value = (String) object.get(key);

			list.put((String) key, value);
			Log.e(LOG_TAG, "key=" + key.toString() + ", value=" + value);
		}

		return list;
	}

	private String getXAPIKey(){
		StringBuilder sb = new StringBuilder();

		sb.append("app_id=");
		sb.append(wimple.getAppid());
		sb.append(",token=");		
		sb.append(wimple.getToken());

		sb.append(",nounce=");
		sb.append(wimple.getSequence().toString());
		sb.append(",timestamp=");
		sb.append(Calendar.getInstance().getTimeInMillis());

		sb.append(",signiture=");
		String signature = Utils.sha1(wimple.getVo42iw5me4vxz() + '|' + wimple.getTokenSecret());
		sb.append(signature);

		//Log.d(LOG_TAG, "XAPIKey = " + sb.toString());
		return sb.toString();
	}
}
