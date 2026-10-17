package kr.blogspot.charlie0301.wimple.impl

import android.util.Log
import kr.blogspot.charlie0301.wimple.impl.util.Utils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.util.Calendar
import java.util.HashMap
import java.util.concurrent.TimeUnit

internal class RestAPIInvoker(private val wimple: IWimpleImpl) {

    private val xapiKey: String
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
            return sb.toString()
        }

    internal enum class HTTPMethod {
        GET, POST, PUT, DELETE
    }

    fun invokeRESTAPI(method: HTTPMethod, path: String, params: String): JSONObject? {
        try {
            Log.d(LOG_TAG, "Invoke REST API, $method , Path=$path")
            val host = wimple.servicehost ?: ""
            val url = if (host.endsWith("/") && path.startsWith("/")) {
                host + path.substring(1)
            } else if (!host.endsWith("/") && !path.startsWith("/")) {
                "$host/$path"
            } else {
                host + path
            }

            val mediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
            val requestBuilder = Request.Builder()
                .url(url)
                .header("X-API-KEY", xapiKey)

            when (method) {
                HTTPMethod.GET -> requestBuilder.get()
                HTTPMethod.POST -> requestBuilder.post(params.toRequestBody(mediaType))
                HTTPMethod.PUT -> requestBuilder.put(params.toRequestBody(mediaType))
                HTTPMethod.DELETE -> requestBuilder.delete()
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val output = response.body?.string() ?: ""

                @Suppress("ConstantConditionIf")
                if (isNeedToPrintResult) {
                    Log.d(LOG_TAG, "result -------------------------------------------------")
                    Log.d(LOG_TAG, output)
                    Log.d(LOG_TAG, "result -------------------------------------------------")
                }

                if (response.code != 200 && response.code != 201) {
                    Log.w(LOG_TAG, "Failed : HTTP error code : ${response.code}")
                    return null
                }

                return JSONObject(output)
            }
        } catch (e: Exception) {
            Log.d(LOG_TAG, "REST Invocation is failed!!!")
            e.printStackTrace()
            return null
        }
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
                if (isNeedToPrintResult) {
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

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}

