package kr.blogspot.charlie0301.wimple.impl;

import android.util.Log;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.spi.service.ServiceFinder;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import kr.blogspot.charlie0301.wimple.impl.util.AndroidServiceIteratorProvider;
import kr.blogspot.charlie0301.wimple.impl.util.SSLClientHelper;
import kr.blogspot.charlie0301.wimple.impl.util.Utils;

class RestAPIInvoker {

    private static final String LOG_TAG = "RestAPIInvoker";
    private final IWimpleImpl wimple;

    enum HTTP_METHOD {GET, POST, PUT, DELETE}

    private static final boolean isNeedToPrintResult = false;

    RestAPIInvoker(IWimpleImpl wimpleImpl) {
        this.wimple = wimpleImpl;
    }

    JSONObject invokeRESTAPI(HTTP_METHOD method, String path, String params) {

        Client client = null;
        WebResource webResource;
        JSONObject object = null;

        try {
            ServiceFinder.setIteratorProvider(new AndroidServiceIteratorProvider<>());

            Log.d(LOG_TAG, "Invoke REST API, " + method.toString() + " , Path=" + path);
            client = SSLClientHelper.createClient();
            webResource = client.resource(wimple.getServicehost() + path);
            ClientResponse response;

            WebResource.Builder wb = webResource.type("application/x-www-form-urlencoded");
            wb = wb.header("X-API-KEY", getXAPIKey());

            switch (method) {
                case GET:
                    response = wb.get(ClientResponse.class);
                    break;

                case POST:
                    response = wb.post(ClientResponse.class, params);
                    break;

                case PUT:
                    response = wb.put(ClientResponse.class, params);
                    break;

                case DELETE:
                    response = wb.delete(ClientResponse.class);
                    break;

                default:
                    throw new Exception("Not supported HTTP Method");
            }

            String output = response.getEntity(String.class);

            if (RestAPIInvoker.isNeedToPrintResult) {
                Log.d(LOG_TAG, "result -------------------------------------------------");
                Log.d(LOG_TAG, output);
                Log.d(LOG_TAG, "result -------------------------------------------------");
            }

            if (response.getStatus() != 200 &&
                    response.getStatus() != 201) {

                throw new RuntimeException("Failed : HTTP error code : "
                        + response.getStatus());
            }

            object = new JSONObject(output);
            client.destroy();
            client = null;
        } catch (Exception e) {
            Log.d(LOG_TAG, "REST Invocation is failed!!!");
            e.printStackTrace();
            object = null;
        } finally {
            if (null != client) {
                client.destroy();
            }
        }
        return object;
    }

    Map<String, String> invokeRESTAPIForMap(String path, String params) {

        Map<String, String> list = new HashMap<>();
        JSONObject object = invokeRESTAPI(HTTP_METHOD.POST, path, params);

        if (null == object) {
            return list;
        }
        /*
		if(!object.get("code").toString().startsWith("2")){
			Log.e(LOG_TAG, "[invokeRESTAPIForMap] Error response - " + path + ", "+ object.get("message").toString());			
			
			int code = Integer.parseInt(object.get("code").toString());
			wimple.handleRESTErrorResponse(code);
			return list;
		}
		*/

        try {
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                String value = object.getString(key);

                list.put(key, value);
                if (RestAPIInvoker.isNeedToPrintResult) {
                    Log.e(LOG_TAG, "key=" + key + ", value=" + value);
                }
            }
        } catch (JSONException e) {
            return list;
        }

        return list;
    }

    private String getXAPIKey() {
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
