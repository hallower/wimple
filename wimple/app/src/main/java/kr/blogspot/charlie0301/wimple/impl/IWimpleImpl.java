package kr.blogspot.charlie0301.wimple.impl;

import org.json.JSONObject;

import java.util.concurrent.Semaphore;

import kr.blogspot.charlie0301.wimple.impl.RestAPIInvoker.HTTPMethod;
import kr.blogspot.charlie0301.wimple.impl.db.EntryDBHandler;
import kr.blogspot.charlie0301.wimple.impl.db.ItemDBHandler;


interface IWimpleImpl {

    // subsystem
    Semaphore getApiAvailableSemaphore(String key);

    ItemDBHandler getLatestItemDBHandler();

    EntryDBHandler getEntryDBHandler();

    ItemDBHandler getMonthlyItemDBHandler();

    // getter

    String getServicehost();

    String getAppid();

    String getVo42iw5me4vxz();

    String getToken();

    String getTokenSecret();

    String getUserID();

    Integer getSequence();

    Boolean isAuthed();

    Integer getRemainedAPICall();

    void setRemainedAPICall(String count);


    void sm(int cmd, Object msg);

    void sm(int cmd, int a1, int a2, Object msg);


    JSONObject invokeRESTAPI(HTTPMethod method, String path, String params);

    void handleRESTErrorResponse(int code);
}
