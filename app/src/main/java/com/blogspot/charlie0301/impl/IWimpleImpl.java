package com.blogspot.charlie0301.impl;

import java.util.concurrent.Semaphore;

import com.blogspot.charlie0301.impl.RestAPIInvoker.HTTP_METHOD;
import com.blogspot.charlie0301.impl.db.EntryDBHandler;
import com.blogspot.charlie0301.impl.db.ItemDBHandler;

import org.json.simple.JSONObject;


public interface IWimpleImpl {

	// subsystem
	public Semaphore getApiAvailableSemaphore(String key);
	
	public ItemDBHandler getLatestItemDBHandler();
	public EntryDBHandler getEntryDBHandler();
	public ItemDBHandler getMonthlyItemDBHandler();
	
	// getter
	
	public abstract String getServicehost();
	
	public abstract String getAppid();

	public abstract String getVo42iw5me4vxz();

	public abstract String getToken();

	public abstract String getTokenSecret();

	public abstract String getUserID();
	
	public abstract Integer getSequence();

	public abstract Boolean isAuthed();
	
	public abstract Integer getRemainedAPICall();
	
	public abstract Integer getTotalAPICall();
	
	public abstract void setRemainedAPICall(String count);
	
	
	public void sm(int cmd, Object msg);

	public void sm(int cmd, int a1, int a2, Object msg);
	
	
	
	public JSONObject invokeRESTAPI(HTTP_METHOD method, String path, String params);
	
	public void handleRESTErrorResponse(int code);
}
