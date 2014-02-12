package kr.blogspot.charlie0301.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import kr.blogspot.charlie0301.model.Account;
import kr.blogspot.charlie0301.model.Entry;
import kr.blogspot.charlie0301.model.Item;
import kr.blogspot.charlie0301.model.Section;
import kr.blogspot.charlie0301.model.UserInfo;


public interface IWimpleResponseListener {
	
	/*
	 * Service Control
	 */
	public void onGetAuthTempToken(boolean status, String tempToken);
	
	public void onGetAuthAccessToken(boolean status, Map<String, String> result);
	
	/*
	 * R, User
	 */
	public void onGetUserInfoResponseReceived(boolean status, UserInfo info);
	
	public void onGetAllSectionResponseReceived(boolean status, Collection<Section> list);
	
	public void onGetAllAccountResponseReceived(boolean status, Collection<Account> list);
	
	/*
	 * R, Entry
	 */
	public void onGetEntriesResponseReceived(boolean status, Collection<Entry> list);
	
	public void onGetLatestEntriesResponseReceived(boolean status, Collection<Entry> list);
	
	
	/*
	 * C, U, Entry
	 */
	public void onMakeEntryResponseReceived(boolean status, String entryDate);
	
	public void onModifyEntryResponseReceived(boolean status, String entryDate);
	
	/*
	 * R, Items
	 */
	public void onGetFrequentItemsResponseReceived(boolean status, Collection<Item> list);
	
	public void onGetLatestItemsResponseReceived(boolean status, Collection<Item> list);
	
	public void onGetMonthlyItemsResponseReceived(boolean status, ArrayList<Item> list);
	
}
