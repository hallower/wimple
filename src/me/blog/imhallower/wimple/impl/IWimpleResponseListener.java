package me.blog.imhallower.wimple.impl;

import java.util.Collection;
import java.util.Map;

import me.blog.imhallower.wimple.model.Account;
import me.blog.imhallower.wimple.model.Entry;
import me.blog.imhallower.wimple.model.Section;

public interface IWimpleResponseListener {
	
	public void onGetAuthTempToken(boolean status, String tempToken);
	
	public void onGetAuthAccessToken(boolean status, Map<String, String> result);
	
	public void onGetAllSectionReceived(boolean status, Collection<Section> list);
	
	public void onGetAllAccountReceived(boolean status, Collection<Account> list);
	
	public void onGetEntriesReceived(boolean status, Collection<Entry> list);
	
	public void onGetLatestEntriesReceived(boolean status, Collection<Entry> list);
	
}
