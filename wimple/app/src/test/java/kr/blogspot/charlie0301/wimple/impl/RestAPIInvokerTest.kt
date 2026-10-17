package kr.blogspot.charlie0301.wimple.impl

import kr.blogspot.charlie0301.wimple.impl.db.EntryDBHandler
import kr.blogspot.charlie0301.wimple.impl.db.ItemDBHandler
import kr.blogspot.charlie0301.wimple.impl.util.Utils
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RestAPIInvokerTest {

    private lateinit var server: MockWebServer
    private lateinit var fakeWimple: FakeWimpleImpl
    private lateinit var invoker: RestAPIInvoker

    private class FakeWimpleImpl(
        var host: String = "",
        var appIdVal: String = "140",
        var appSecretVal: String = "my_secret_key",
        var tokenVal: String = "my_auth_token",
        var tokenSecretVal: String = "my_token_secret",
        var seqVal: Int = 777
    ) : IWimpleImpl {
        override fun getServicehost(): String = host
        override fun getAppid(): String = appIdVal
        override fun getVo42iw5me4vxz(): String = appSecretVal
        override fun getToken(): String = tokenVal
        override fun getTokenSecret(): String = tokenSecretVal
        override fun getUserID(): String = "test_user"
        override fun getSequence(): Int = seqVal
        override fun isAuthed(): Boolean = true
        override fun getRemainedAPICall(): Int = 100
        override fun setRemainedAPICall(count: String?) {}
        override fun getApiAvailableSemaphore(key: String?): Semaphore? = null
        override fun getLatestItemDBHandler(): ItemDBHandler? = null
        override fun getEntryDBHandler(): EntryDBHandler? = null
        override fun getMonthlyItemDBHandler(): ItemDBHandler? = null
        override fun sm(cmd: Int, msg: Any?) {}
        override fun sm(cmd: Int, a1: Int, a2: Int, msg: Any?) {}
        override fun invokeRESTAPI(method: RestAPIInvoker.HTTPMethod?, path: String?, params: String?): JSONObject? = null
        override fun handleRESTErrorResponse(code: Int) {}
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        fakeWimple = FakeWimpleImpl(host = server.url("/").toString())
        invoker = RestAPIInvoker(fakeWimple)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun get_success_sendsCorrectHeadersAndReturnsJson() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"code":200,"result":{"user_id":"charlie"}}""")
        )

        val json = invoker.invokeRESTAPI(RestAPIInvoker.HTTPMethod.GET, "api/user.json", "")

        assertNotNull(json)
        assertEquals(200, json!!.getInt("code"))
        assertEquals("charlie", json.getJSONObject("result").getString("user_id"))

        val recordedRequest = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        assertEquals("GET", recordedRequest!!.method)
        assertEquals("/api/user.json", recordedRequest.path)

        val xApiKey = recordedRequest.getHeader("X-API-KEY")
        assertNotNull(xApiKey)
        assertTrue("Must contain app_id=140", xApiKey!!.contains("app_id=140"))
        assertTrue("Must contain token=my_auth_token", xApiKey.contains("token=my_auth_token"))
        assertTrue("Must contain nounce=777", xApiKey.contains("nounce=777"))
        assertTrue("Must contain timestamp=", xApiKey.contains("timestamp="))

        val expectedSignature = Utils.sha1("my_secret_key|my_token_secret")
        assertTrue("Must contain signature=$expectedSignature", xApiKey.contains("signiture=$expectedSignature"))
    }

    @Test
    fun post_success_sendsBodyAndParamsWithContentType() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"code":200,"result":{"entry_id":"12345"}}""")
        )

        val params = "entry_date=20261017&item=점심&money=10000"
        val json = invoker.invokeRESTAPI(RestAPIInvoker.HTTPMethod.POST, "api/entries.json", params)

        assertNotNull(json)
        assertEquals(200, json!!.getInt("code"))
        assertEquals("12345", json.getJSONObject("result").getString("entry_id"))

        val recordedRequest = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        assertEquals("POST", recordedRequest!!.method)
        assertEquals("/api/entries.json", recordedRequest.path)
        val contentType = recordedRequest.getHeader("Content-Type")
        assertNotNull(contentType)
        assertTrue("Content-type must contain form urlencoded", contentType!!.contains("application/x-www-form-urlencoded"))
        assertEquals(params, recordedRequest.body.readUtf8())
    }

    @Test
    fun put_success_sendsParamsAndReturnsJson() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"code":200,"result":"updated"}""")
        )

        val params = "money=12000"
        val json = invoker.invokeRESTAPI(RestAPIInvoker.HTTPMethod.PUT, "api/entries/12345", params)

        assertNotNull(json)
        assertEquals("updated", json!!.getString("result"))

        val recordedRequest = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        assertEquals("PUT", recordedRequest!!.method)
        assertEquals("/api/entries/12345", recordedRequest.path)
        assertEquals(params, recordedRequest.body.readUtf8())
    }

    @Test
    fun delete_success_returnsJson() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"code":200,"result":"deleted"}""")
        )

        val json = invoker.invokeRESTAPI(RestAPIInvoker.HTTPMethod.DELETE, "api/entries/12345", "")

        assertNotNull(json)
        assertEquals("deleted", json!!.getString("result"))

        val recordedRequest = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        assertEquals("DELETE", recordedRequest!!.method)
        assertEquals("/api/entries/12345", recordedRequest.path)
    }

    @Test
    fun status201_treatedAsSuccess() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"code":201,"status":"created"}""")
        )

        val json = invoker.invokeRESTAPI(RestAPIInvoker.HTTPMethod.POST, "api/test_created", "")
        assertNotNull(json)
        assertEquals(201, json!!.getInt("code"))
    }

    @Test
    fun httpErrorStatusCodes_returnNull() {
        val errorCodes = listOf(400, 401, 403, 404, 500)
        for (code in errorCodes) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(code)
                    .setHeader("Content-Type", "application/json; charset=utf-8")
                    .setBody("""{"code":$code,"error":"failed"}""")
            )

            val json = invoker.invokeRESTAPI(RestAPIInvoker.HTTPMethod.GET, "api/error_$code", "")
            assertNull("Status $code must return null", json)
        }
    }

    @Test
    fun malformedJson_returnsNull() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain")
                .setBody("Not valid JSON at all")
        )

        val json = invoker.invokeRESTAPI(RestAPIInvoker.HTTPMethod.GET, "api/malformed", "")
        assertNull(json)
    }

    @Test
    fun invokeRESTAPIForMap_success_parsesAllKeysToStrings() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(
                    """
                    {
                        "code": 200,
                        "token": "final_access_token",
                        "token_secret": "final_token_secret",
                        "user_id": "u4321"
                    }
                    """.trimIndent()
                )
        )

        val map = invoker.invokeRESTAPIForMap("app_auth/access_token", "pin=123456")

        assertEquals("final_access_token", map["token"])
        assertEquals("final_token_secret", map["token_secret"])
        assertEquals("u4321", map["user_id"])
        assertEquals("200", map["code"])
    }

    @Test
    fun invokeRESTAPIForMap_onFailure_returnsEmptyMap() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"code":400}""")
        )

        val map = invoker.invokeRESTAPIForMap("app_auth/failed_token", "")
        assertTrue(map.isEmpty())
    }
}
