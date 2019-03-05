package kr.blogspot.charlie0301.wimple.impl

import android.util.Log
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.spi.service.ServiceFinder
import kr.blogspot.charlie0301.wimple.impl.util.AndroidServiceIteratorProvider
import kr.blogspot.charlie0301.wimple.impl.util.SSLClientHelper
import kr.blogspot.charlie0301.wimple.impl.util.Utils
import org.json.JSONException
import org.json.JSONObject
import java.util.*

internal class RestAPIInvoker(private val wimple: IWimpleImpl) {

    private
    val xapiKey: String
        get() {
            val sb = StringBuilder()

            sb.append("app_id=")
            sb.append(wimple.appid)
            sb.append(",token=")
            sb.append(wimple.token)

            sb.append(",nounce=")
            sb.append(wimple.sequence!!.toString())
            sb.append(",timestamp=")
            sb.append(Calendar.getInstance().timeInMillis)

            sb.append(",signiture=")
            val signature = Utils.sha1(wimple.vo42iw5me4vxz + '|'.toString() + wimple.tokenSecret)
            sb.append(signature)
            //Log.d(LOG_TAG, "XAPIKey = " + sb.toString());
            return sb.toString()
        }

    internal enum class HTTPMethod {
        GET, POST, PUT, DELETE
    }

    fun invokeRESTAPI(method: HTTPMethod, path: String, params: String): JSONObject? {

        var client: Client? = null
        val webResource: WebResource
        lateinit var jsonObject: JSONObject

        try {
            ServiceFinder.setIteratorProvider(AndroidServiceIteratorProvider<Any>())
        } catch (e: Exception) {
            Log.i(LOG_TAG, "ServiceFinder.setIteratorProvider is already set")
        }

        try {
            Log.d(LOG_TAG, "Invoke REST API, $method , Path=$path")
            client = SSLClientHelper.createClient()
            webResource = client!!.resource(wimple.servicehost + path)
            val response: ClientResponse

            var wb: WebResource.Builder = webResource.type("application/x-www-form-urlencoded")
            wb = wb.header("X-API-KEY", xapiKey)

            response = when (method) {
                RestAPIInvoker.HTTPMethod.GET -> wb.get(ClientResponse::class.java)
                RestAPIInvoker.HTTPMethod.POST -> wb.post(ClientResponse::class.java, params)
                RestAPIInvoker.HTTPMethod.PUT -> wb.put(ClientResponse::class.java, params)
                RestAPIInvoker.HTTPMethod.DELETE -> wb.delete(ClientResponse::class.java)
            }

            val output = response.getEntity(String::class.java)

            @Suppress("ConstantConditionIf")
            if (RestAPIInvoker.isNeedToPrintResult) {
                Log.d(LOG_TAG, "result -------------------------------------------------")
                Log.d(LOG_TAG, output)
                Log.d(LOG_TAG, "result -------------------------------------------------")
            }

            if (response.status != 200 && response.status != 201) {

                throw RuntimeException("Failed : HTTP error code : " + response.status)
            }

           jsonObject = JSONObject(output)
            client.destroy()
            client = null
        } catch (e: Exception) {
            Log.d(LOG_TAG, "REST Invocation is failed!!!")
            e.printStackTrace()
            return null
        } finally {
            client?.destroy()
        }
        return jsonObject
    }

    fun invokeRESTAPIForMap(path: String, params: String): Map<String, String> {

        val list = HashMap<String, String>()
        val `object` = invokeRESTAPI(HTTPMethod.POST, path, params) ?: return list

        try {
            val iterator = `object`.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val value = `object`.getString(key)

                list[key] = value
                @Suppress("ConstantConditionIf")
                if (RestAPIInvoker.isNeedToPrintResult) {
                    Log.e(LOG_TAG, "key=$key, value=$value")
                }
            }
        } catch (e: JSONException) {
            return list
        }

        return list
    }

    companion object {

        private const val LOG_TAG = "RestAPIInvoker"

        private const val isNeedToPrintResult = false
    }
}
