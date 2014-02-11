package kr.blogspot.charlie0301.impl.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import android.content.Context;
import android.util.Log;

public class RemoteContent {

	private static final String LOG_TAG = "RemoteContent";
	private ServerCommunication comm = new ServerCommunication();

	private static final RemoteContent INSTANCE = new RemoteContent();

	public static RemoteContent getInstance(){
		return INSTANCE;
	}

	private RemoteContent(){

	}	

	public String uploadPicture(String restURL, String userMessagingID, String sessionID, String filePath){
		String UPLOAD_IMAGE = restURL + "/rest/promise/file/" + userMessagingID + "/" + sessionID + "/upload";
		String newURL = "";

		try {
			newURL = upload(UPLOAD_IMAGE, filePath);
		}catch(Exception e){
			e.printStackTrace();
			newURL = "";
		}

		return newURL;
	}

	public String downloadPicture(Context context, String url){

		assert null == context;

		if(null == url){
			return "";
		}

		if(false == url.startsWith("http://")){
			return "";
		}
		
		url = url.replaceAll("\\s+", "");

		int pos = url.length();
		for(int i = 0; i < 3 ; i++){
			pos = url.lastIndexOf("/", pos - 1);
		}

		assert pos == -1;

		String convertedURL = url.substring(pos + 1, url.length());
		convertedURL = convertedURL.replace("/", ".").replace("@", ".");

		String localFilePath = "" + context.getExternalFilesDir(null) + "/" + convertedURL;

		if(false == download(url, localFilePath, false)){
			return "";
		}

		return localFilePath;
	}

	// Synchronous API
	public String upload(String url, String path){

		if(false == url.startsWith("http://")){
			return "";
		}
		
		File file = new File(path);

		if(false == file.isFile()){
			return "";
		}

		//Log.d(LOG_TAG, "UPLOAD START " + path + " ===> " + url);
		String returnedURL = comm.uploadUserPhoto(url, file);
		
		return returnedURL;
	}

	// Synchronous API
	public boolean download(String url, String path, boolean overwrite){

		if(false == url.startsWith("http://")){
			return false;
		}

		File file = new File(path);
		if(!overwrite && file.exists())
			return true;

		if(false == file.isFile()){
			try {
				file.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}
		}

		//Log.d(LOG_TAG, "DOWNLOAD START " + path + " ===> " + url);
		return comm.downloadUserPhoto(url, file);
	}


	class ServerCommunication{
		private DefaultHttpClient mHttpClient;

		public ServerCommunication() {
			HttpParams params = new BasicHttpParams();
			params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
			mHttpClient = new DefaultHttpClient(params);
		}

		public boolean downloadUserPhoto(String url, File output) {
			FileOutputStream outputStream = null; 

			try{
				HttpClient client = new DefaultHttpClient();
				HttpGet request = new HttpGet(url);
				HttpResponse response = client.execute(request);				

				if(HttpStatus.SC_OK != response.getStatusLine().getStatusCode()){
					Log.e(LOG_TAG, "DOWNLOAD FAILED > with \n" + response.getStatusLine().toString() + " ===> " + url);
					// This can be a problem!!!, let's check the log carefully
					output.delete();
					return false;
				}				

				outputStream = new FileOutputStream(output);
				InputStream input = response.getEntity().getContent();
				int read = 0;
				byte[] bytes = new byte[4096];

				while((read = input.read(bytes)) != -1){
					outputStream.write(bytes, 0, read);
				}

			}catch(Exception e){
				e.printStackTrace();
				Log.e(LOG_TAG, "DOWNLOAD FAILED > " + url);
				// This can be a problem!!!, let's check the log carefully
				output.delete();				
				return false;
			}finally{
				try{
					outputStream.close();	
				}catch(Exception e){

				}				
			}

			Log.d(LOG_TAG, "DOWNLOAD DONE  > " + url);
			return true;
		}

		public String uploadUserPhoto(String url, File image) {
			try {
				HttpPost httppost = new HttpPost(url);
				MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE); 

				multipartEntity.addPart("file", new FileBody(image));
				httppost.setEntity(multipartEntity);

				// Use Synchronous transfer that suitable for the Promise Design Concept.
				//mHttpClient.execute(httppost, new PhotoUploadResponseHandler());
				HttpResponse response = mHttpClient.execute(httppost);

				return new PhotoUploadResponseHandler().handleResponse(response).toString();

			} catch (Exception e) {
				e.printStackTrace();
				return "";
			}
		}

		private class PhotoUploadResponseHandler implements ResponseHandler<Object> {

			@Override
			public Object handleResponse(HttpResponse response)
					throws ClientProtocolException, IOException {

				// TODO : check 1xx status situation
				if(HttpStatus.SC_OK != response.getStatusLine().getStatusCode()){
					Log.e(LOG_TAG, "UPLOAD FAILED > with \n" + response.getStatusLine().toString());
					return response.getStatusLine().toString();
				}

				HttpEntity r_entity = response.getEntity();
				String responseString = EntityUtils.toString(r_entity);
				Log.d("LOG_TAG", "UPLOAD DONE > \n" + responseString);

				return responseString;
			}

		}
	}
}
