package me.blog.imhallower.wimple.impl;

import java.util.Collection;
import java.util.Map;

import me.blog.imhallower.wimple.model.Account;
import me.blog.imhallower.wimple.model.Entry;
import me.blog.imhallower.wimple.model.Section;
import me.blog.imhallower.wimple.model.UserInfo;

public interface IWimpleResponseListener {
	
	public void onGetAuthTempToken(boolean status, String tempToken);
	
	public void onGetAuthAccessToken(boolean status, Map<String, String> result);
	
	public void onGetUserInfoResponseReceived(boolean status, UserInfo info);
	
	public void onGetAllSectionResponseReceived(boolean status, Collection<Section> list);
	
	public void onGetAllAccountResponseReceived(boolean status, Collection<Account> list);
	
	public void onGetEntriesResponseReceived(boolean status, Collection<Entry> list);
	
	public void onGetLatestEntriesResponseReceived(boolean status, Collection<Entry> list);
	
	public void onMakeEntryResponseReceived(boolean status);
	
}
