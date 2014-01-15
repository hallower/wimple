package me.blog.imhallower.wimple.impl;

import me.blog.imhallower.wimple.impl.RestAPIInvoker.HTTP_METHOD;

import org.json.simple.JSONObject;


public interface IWimpleImpl {

	public abstract String getServicehost();
	
	public abstract String getAppid();

	public abstract String getVo42iw5me4vxz();

	public abstract String getToken();

	public abstract String getTokenSecret();

	public abstract String getUserID();
	
	public abstract Integer getSequence();

	public abstract Boolean isAuthed();
	
	public abstract Integer getRemainedAPICall();
	
	public abstract void setRemainedAPICall(String count);
	
	
	public void sm(int cmd, Object msg);

	public void sm(int cmd, int a1, int a2, Object msg);
	
	
	
	public JSONObject invokeRESTAPI(HTTP_METHOD method, String path, String params);
}
