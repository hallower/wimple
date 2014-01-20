package kr.blogspot.charlie0301.impl;

import java.util.Collection;
import java.util.Map;

import kr.blogspot.charlie0301.model.Account;
import kr.blogspot.charlie0301.model.Entry;
import kr.blogspot.charlie0301.model.Item;
import kr.blogspot.charlie0301.model.Section;
import kr.blogspot.charlie0301.model.UserInfo;


public interface IWimpleResponseListener {
	
	public void onGetAuthTempToken(boolean status, String tempToken);
	
	public void onGetAuthAccessToken(boolean status, Map<String, String> result);
	
	public void onGetUserInfoResponseReceived(boolean status, UserInfo info);
	
	public void onGetAllSectionResponseReceived(boolean status, Collection<Section> list);
	
	public void onGetAllAccountResponseReceived(boolean status, Collection<Account> list);
	
	public void onGetEntriesResponseReceived(boolean status, Collection<Entry> list);
	
	public void onGetLatestEntriesResponseReceived(boolean status, Collection<Entry> list);
	
	public void onMakeEntryResponseReceived(boolean status);
	
	public void onGetFrequentItemsResponseReceived(boolean status, Collection<Item> list);
	
	public void onGetLatestItemsResponseReceived(boolean status, Collection<Item> list);
}
