package kr.blogspot.charlie0301.wimple.impl.util;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class RemoteContent {

    private static final String LOG_TAG = "RemoteContent";
    private ServerCommunication comm = new ServerCommunication();

    private static final RemoteContent INSTANCE = new RemoteContent();

    public static RemoteContent getInstance() {
        return INSTANCE;
    }

    private RemoteContent() {

    }

    /*
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
    */
    public String downloadPicture(Context context, String url) {

        assert null == context;

        if (null == url) {
            return "";
        }

        if (!url.startsWith("http://")) {
            return "";
        }

        url = url.replaceAll("\\s+", "");

        int pos = url.length();
        for (int i = 0; i < 3; i++) {
            pos = url.lastIndexOf("/", pos - 1);
        }

        assert pos == -1;

        String convertedURL = url.substring(pos + 1, url.length());
        convertedURL = convertedURL.replace("/", ".").replace("@", ".");

        assert context != null;
        String localFilePath = "" + context.getExternalFilesDir(null) + "/" + convertedURL;

        if (!download(url, localFilePath, false)) {
            return "";
        }

        return localFilePath;
    }

    /*
        // Synchronous API
        public String upload(String url, String path){

            if(!url.startsWith("http://")){
                return "";
            }

            File file = new File(path);

            if(!file.isFile()){
                return "";
            }

            //Log.d(LOG_TAG, "UPLOAD START " + path + " ===> " + url);
            String returnedURL = comm.uploadUserPhoto(url, file);

            return returnedURL;
        }
    */
    // Synchronous API
    public boolean download(String url, String path, boolean overwrite) {

        if (!url.startsWith("http://") &&
                !url.startsWith("https://")) {
            return false;
        }

        File file = new File(path);
        if (!overwrite && file.exists())
            return true;

        if (!file.isFile()) {
            try {
                //noinspection ResultOfMethodCallIgnored
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

    public String getTitlePartOfPage(String url) {
        return comm.getPartialPageIncludingTitle(url);
    }

    private class ServerCommunication {

        ServerCommunication() {
        }

        boolean downloadUserPhoto(String url, File output) {

            HttpURLConnection urlConnection = null;
            FileOutputStream outputStream = null;

            try {
                urlConnection = (HttpURLConnection) ((new URL(url)).openConnection());

                urlConnection.setConnectTimeout(1000);
                urlConnection.setUseCaches(false);

                if (HttpURLConnection.HTTP_OK != urlConnection.getResponseCode()) {
                    Log.e(LOG_TAG, "DOWNLOAD FAILED > with \n" + urlConnection.getResponseCode() + " ===> " + url);
                    // This can be a problem!!!, let's check the log carefully
                    //noinspection ResultOfMethodCallIgnored
                    output.delete();
                    return false;
                }

                outputStream = new FileOutputStream(output);
                InputStream inputStream = urlConnection.getInputStream();

                byte[] buff = new byte[4096];
                int bufferLength;

                while (-1 != (bufferLength = inputStream.read(buff)))
                    outputStream.write(buff, 0, bufferLength);

            } catch (Exception e) {
                e.printStackTrace();
                Log.e(LOG_TAG, "DOWNLOAD FAILED > " + url);
                // This can be a problem!!!, let's check the log carefully
                //noinspection ResultOfMethodCallIgnored
                output.delete();
                return false;
            } finally {
                try {
                    if (null != urlConnection)
                        urlConnection.disconnect();
                    if (null != outputStream)
                        outputStream.close();
                } catch (Exception ignored) {
                }
            }
            Log.d(LOG_TAG, "DOWNLOAD DONE  > " + url);
            return true;
        }

        String getPartialPageIncludingTitle(String url) {

            HttpURLConnection urlConnection = null;
            StringBuffer response = new StringBuffer();

            try {
                urlConnection = (HttpURLConnection) ((new URL(url)).openConnection());

                urlConnection.setConnectTimeout(1000);
                urlConnection.setUseCaches(false);

                if (HttpURLConnection.HTTP_OK != urlConnection.getResponseCode()) {
                    Log.e(LOG_TAG, "Title Getting FAILED > with \n" + urlConnection.getResponseCode() + " ===> " + url);
                    return response.toString();
                }

                String charset = new String("euc-kr");
                String contentType = urlConnection.getHeaderField("Content-Type");
                if (0 < contentType.length()) {
                    Log.d(LOG_TAG, "Content-Type = " + contentType);

                    int pos = contentType.indexOf("charset=");
                    if (0 < pos) {
                        charset = contentType.substring(pos + 8).toLowerCase(Locale.US);
                        int endPos = charset.indexOf(";");
                        if (0 < endPos)
                            charset = charset.substring(0, endPos - 1);
                        Log.d(LOG_TAG, "charset = " + charset);
                    }
                }

                BufferedReader buffer = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), charset));
                String s;

                while ((s = buffer.readLine()) != null) {
                    response.append(s);
                    if (response.toString().contains("</title>") ||
                            response.toString().contains("</TITLE>")) {
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(LOG_TAG, "Title Getting  FAILED > " + url);
                return response.toString();
            } finally {
                try {
                    if (null != urlConnection)
                        urlConnection.disconnect();
                } catch (Exception ignored) {
                }
            }
            Log.d(LOG_TAG, "Title Getting  DONE  > " + url);
            return response.toString();
        }

/*
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
*/
    }
}
