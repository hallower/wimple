package kr.blogspot.charlie0301.impl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import kr.blogspot.charlie0301.impl.RestAPIInvoker.HTTP_METHOD;
import kr.blogspot.charlie0301.impl.WimpleImpl.CommandID;
import kr.blogspot.charlie0301.impl.WimpleImpl.Path;
import kr.blogspot.charlie0301.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.model.Account;
import kr.blogspot.charlie0301.model.Entry;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.util.Log;

public class EntryManager {

	private static final String LOG_TAG = "EntryManager";

	private final IWimpleImpl wimpl;	

	private final String formatEntryPost = "[{" +
			"\"entry_date\" : %s," +
			"\"l_account\" : \"%s\"," +
			"\"l_account_id\" : \"%s\"," +
			"\"r_account\" : \"%s\"," +
			"\"r_account_id\" : \"%s\"," +
			"\"item\" : \"%s\"," +
			"\"money\" : %f," +
			"\"memo\" : \"%s\"" +
			"}]";

	private final String formatEntryPut = "" +
			"&entry_date=%s" +			
			"&l_account=%s" +
			"&l_account_id=%s" +
			"&r_account=%s" +
			"&r_account_id=%s" +
			"&item=%s" +
			"&money=%f" +
			"";

	public EntryManager(IWimpleImpl wimpl) {
		super();
		this.wimpl = wimpl;
	}

	public boolean getStoredEntries(){

		if(null == wimpl.getEntryDBHandler()){
			return false;
		}

		new Thread(){
			@Override
			public void run() {
				Calendar cl = Calendar.getInstance();
				cl.add(Calendar.MONTH, -1);
				Log.d(LOG_TAG, "[StoredEntries] Flushing entries before " + cl.getTime().toString());
				wimpl.getEntryDBHandler().cleanOldEntries(cl.getTimeInMillis());

				Log.d(LOG_TAG, "[StoredEntries] Providing Stored Entries from Cache.");
				wimpl.sm(CommandID.CMD_GET_ENTRIES, 1, 0, wimpl.getEntryDBHandler().getAllEntrys());
			}			
		}.start();

		return true;
	}

	public boolean getAllEntries(String sectionID, String latestDate, String oldestDate){

		new GetAllEntriesTaskThread(sectionID, latestDate, oldestDate, 0).start();		
		return true;
	}

	public boolean getAllEntries(String sectionID, String latestDate, String oldestDate, int count){

		new GetAllEntriesTaskThread(sectionID, latestDate, oldestDate, count).start();		
		return true;
	}


	public boolean getLatestEntries(String sectionID, int count, boolean noDuplicate){

		new GetLatestEntriesTaskThread(sectionID, count).start();		
		return true;
	}

	public boolean makeEntry(String sectionID, Long date, Account left, Account right, 
			String title, Double amount, String memo){

		new PostEntryTaskThread(sectionID, date, left, right, title, amount, memo).start();		
		return true;
	}

	public boolean modifyEntry(String sectionID, String entryID, String date, Account left, Account right, 
			String title, Double amount, String memo){

		new PutEntryTaskThread(sectionID, entryID, date, left, right, title, amount, memo).start();		
		return true;
	}
	
	public boolean removeEntry(String sectionID, String entryID){

		new DeleteEntryTaskThread(sectionID, entryID).start();		
		return true;
	}

	private class GetAllEntriesTaskThread extends Thread{

		final String sectionID;
		final String latestDate;
		final String oldestDate;
		final int count;

		GetAllEntriesTaskThread(String sectionID, String latestDate, String oldestDate, int count){
			this.sectionID = sectionID;
			this.latestDate = latestDate;
			this.oldestDate = oldestDate;
			this.count = count;
		}

		@Override
		public void run() {

			try{

				Collection<Entry> list = new ArrayList<Entry>();
				String path = "?section_id=" + sectionID + "&start_date=" + oldestDate + "&end_date=" + latestDate;

				if(0 > count){
					path += "&limit=" + count;
				}else{
					path += "&limit=40";
				}

				JSONObject json = wimpl.invokeRESTAPI(HTTP_METHOD.GET, Path.ENTRIES_ALL + path, "");
				if(null == json){
					Log.e(LOG_TAG, "[AllEntries] Error response - null returned");
					wimpl.sm(CommandID.CMD_GET_ENTRIES, 0, 0, list);
					return;
				}

				if(false == json.get("code").toString().startsWith("2")){
					Log.e(LOG_TAG, "[AllEntries] Error response - " + json.get("message").toString());
					wimpl.sm(CommandID.CMD_GET_ENTRIES, 0, 0, list);

					int code = Integer.parseInt(json.get("code").toString());
					wimpl.handleRESTErrorResponse(code);
					return;
				}

				wimpl.setRemainedAPICall(json.get("rest_of_api").toString());
				JSONObject results = (JSONObject) json.get("results");
				for(Object type : results.keySet()){

					if(0 != type.toString().compareTo("rows")){
						continue;
					}

					JSONArray rows  = (JSONArray) results.get(type);
					for(int i = 0; i < rows.size(); i++){
						JSONObject row = (JSONObject) rows.get(i);

						if(0 == row.get("l_account_id").toString().compareToIgnoreCase("x0") ||
								0 == row.get("r_account_id").toString().compareToIgnoreCase("x0") ){
							// TODO : handle this as removed item.
							continue;
						}

						// to hide unnecessary entries
						if(row.get("item").toString().startsWith("Adjusted to close")){
							continue;
						}

						Entry item = new Entry(row);
						String balance = row.get("total").toString();
						if(null != balance && 
								false == balance.isEmpty()){
							item.setBalance(balance);
						}

						list.add(item);
					}
				}

				if(list.isEmpty()){
					wimpl.sm(CommandID.CMD_GET_ENTRIES, 0, 0, list);
					return;
				}

				Log.d(LOG_TAG, "[AllEntries] Providing All Entries from Server");
				wimpl.getEntryDBHandler().insert(list);
				wimpl.sm(CommandID.CMD_GET_ENTRIES, 1, 0, list);
				
			}finally{
				wimpl.getApiAvailableSemaphore("getAllEntries").release();
			}
		}
	}

	private class GetLatestEntriesTaskThread extends Thread{

		final String sectionID;
		final int count;

		GetLatestEntriesTaskThread(String sectionID, int count){
			this.sectionID = sectionID;
			this.count = count;
		}

		@Override
		public void run() {

			Collection<Entry> list = new ArrayList<Entry>();
			String path = "?section_id=" + sectionID;

			if(0 > count){
				path += "&limit=" + count;
			}

			JSONObject json = null; 
			json = wimpl.invokeRESTAPI(HTTP_METHOD.GET, Path.ENTRIES_LATEST + path, "");

			if(null == json){
				Log.e(LOG_TAG, "[LatestEntries] Error response - null returned");
				wimpl.sm(CommandID.CMD_GET_LATEST_ENTRIES, 0, 0, list);
				return;
			}

			if(false == json.get("code").toString().startsWith("2")){
				Log.e(LOG_TAG, "[LatestEntries] Error response - " + json.get("message").toString());
				wimpl.sm(CommandID.CMD_GET_LATEST_ENTRIES, 0, 0, list);

				int code = Integer.parseInt(json.get("code").toString());
				wimpl.handleRESTErrorResponse(code);
				return;
			}

			wimpl.setRemainedAPICall(json.get("rest_of_api").toString());
			JSONArray results = (JSONArray) json.get("results");
			for(int i = 0; i < results.size(); i++){
				JSONObject row = (JSONObject) results.get(i);

				if(0 == row.get("l_account_id").toString().compareToIgnoreCase("x0") ||
						0 == row.get("r_account_id").toString().compareToIgnoreCase("x0") ){
					// TODO : handle this as removed item.
					continue;
				}

				// to hide unnecessary entries
				if(row.get("item").toString().startsWith("Adjusted to close")){
					continue;
				}

				Entry item = new Entry(row);
				String balance = row.get("total").toString();
				if(null != balance && 
						false == balance.isEmpty()){
					item.setBalance(balance);
				}

				list.add(item);
			}
			Log.d(LOG_TAG, "[LatestEntries] Providing Latest Entries from Server");
			wimpl.sm(CommandID.CMD_GET_LATEST_ENTRIES, 1, 0, list);
		}			

	}

	private class PostEntryTaskThread extends Thread{

		final String sectionID;
		final Long date;
		final Account left;
		final Account right;
		final String title;
		final Double amount;
		final String memo;

		PostEntryTaskThread(String sectionID, Long date, Account left, Account right, 
				String title, Double amount, String memo){
			this.sectionID = sectionID;
			this.date = date;
			this.left = left;
			this.right = right;
			this.title = title;
			this.amount = amount;
			this.memo = memo;
		}

		@Override
		public void run() {

			String pushingContent = String.format(DateFormatUtils.getDefaultLocale(), 
					formatEntryPost, 
					DateFormatUtils.getServerDateFormat().format(new Date(date)), 
					left.getWhat(),
					left.getId(),
					right.getWhat(),
					right.getId(),
					title,
					amount,
					memo
					);

			String path = "section_id=" + sectionID + "&data_type=json" + "&entries=" + pushingContent;

			//Log.d(LOG_TAG, path);

			JSONObject json = wimpl.invokeRESTAPI(HTTP_METHOD.POST, Path.ENTRIES_LATEST, path);

			if(null == json){
				Log.e(LOG_TAG, "[PostEntry] Error response - null returned");
				wimpl.sm(CommandID.CMD_POST_ENTRY, 0, 0, "");
				return;
			}

			if(false == json.get("code").toString().startsWith("2")){
				Log.e(LOG_TAG, "[PostEntry] Error response - " + json.get("message").toString());
				wimpl.sm(CommandID.CMD_POST_ENTRY, 0, 0, "");
				return;
			}

			wimpl.setRemainedAPICall(json.get("rest_of_api").toString());
			JSONArray results = (JSONArray) json.get("results");
			JSONObject row = (JSONObject) results.get(0);			

			Log.d(LOG_TAG, "[PostEntry] Providing response");
			wimpl.sm(CommandID.CMD_POST_ENTRY, 1, 0, row.get("entry_date").toString());
		}

	}

	private class PutEntryTaskThread extends Thread{

		final String sectionID;
		final String entryID;
		final String date;
		final Account left;
		final Account right;
		final String title;
		final Double amount;
		final String memo;

		PutEntryTaskThread(String sectionID, String entryID, String date, Account left, Account right, 
				String title, Double amount, String memo){
			this.sectionID = sectionID;
			this.entryID = entryID;
			this.date = date;
			this.left = left;
			this.right = right;
			this.title = title;
			this.amount = amount;
			this.memo = memo;
		}

		@Override
		public void run() {

			String pushingContent = String.format(DateFormatUtils.getDefaultLocale(),
					formatEntryPut,
					date, 
					left.getWhat(),
					left.getId(),
					right.getWhat(),
					right.getId(),
					title,
					amount
					);

			String path = "section_id=" + sectionID + "&data_type=json" + pushingContent;
			if(false == memo.isEmpty()){
				path += "&memo=" + memo;
			}

			//Log.d(LOG_TAG, path);

			JSONObject json = wimpl.invokeRESTAPI(HTTP_METHOD.PUT, Path.ENTRIES_MODIFY + entryID + ".json_array", path);

			if(null == json){
				Log.e(LOG_TAG, "[PutEntry] Error response - null returned");
				wimpl.sm(CommandID.CMD_PUT_ENTRY, 0, 0, new Entry());
				return;
			}

			if(false == json.get("code").toString().startsWith("2")){
				Log.e(LOG_TAG, "[PutEntry] Error response -  - " + json.get("message").toString());
				wimpl.sm(CommandID.CMD_PUT_ENTRY, 0, 0, new Entry());
				return;
			}

			wimpl.setRemainedAPICall(json.get("rest_of_api").toString());
			JSONObject row = (JSONObject) json.get("results");
			
			Entry item = new Entry(row);

			Log.d(LOG_TAG, "[PutEntry] Providing response");
			wimpl.sm(CommandID.CMD_PUT_ENTRY, 1, 0, item);
		}
	}
	
	private class DeleteEntryTaskThread extends Thread{

		@SuppressWarnings("unused")
		final String sectionID;
		final String entryID;

		DeleteEntryTaskThread(String sectionID, String entryID){
			this.sectionID = sectionID;
			this.entryID = entryID;
		}

		@Override
		public void run() {

			JSONObject json = wimpl.invokeRESTAPI(HTTP_METHOD.DELETE, Path.ENTRIES_REMOVE + entryID + ".json_array", "");

			if(null == json){
				Log.e(LOG_TAG, "[DeleteEntry] Error response - null returned");
				wimpl.sm(CommandID.CMD_DELETE_ENTRY, 0, 0, "");
				return;
			}

			if(false == json.get("code").toString().startsWith("2")){
				Log.e(LOG_TAG, "[DeleteEntry] Error response -  - " + json.get("message").toString());
				wimpl.sm(CommandID.CMD_DELETE_ENTRY, 0, 0, "");
				return;
			}

			Log.d(LOG_TAG, "[DeleteEntry] Providing response");
			wimpl.getEntryDBHandler().remove(entryID);
			wimpl.sm(CommandID.CMD_DELETE_ENTRY, 1, 0, entryID);
		}
	}
}
