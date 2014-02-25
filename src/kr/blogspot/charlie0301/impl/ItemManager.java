package kr.blogspot.charlie0301.impl;

import java.util.ArrayList;
import java.util.Collection;

import kr.blogspot.charlie0301.impl.RestAPIInvoker.HTTP_METHOD;
import kr.blogspot.charlie0301.impl.WimpleImpl.CommandID;
import kr.blogspot.charlie0301.impl.WimpleImpl.Path;
import kr.blogspot.charlie0301.model.Item;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.util.Log;

public class ItemManager {

	private static final String LOG_TAG = "ItemManager";
	
	private final IWimpleImpl wimpl;
	

	public ItemManager(IWimpleImpl wimpl) {
		super();
		this.wimpl = wimpl;
	}

	
	
	public boolean getFrequentItems(String sectionID){

		new GetFrequentItemsTaskThread(sectionID).start();		
		return true;
	}

	private class GetFrequentItemsTaskThread extends Thread{

		final String sectionID;

		GetFrequentItemsTaskThread(String sectionID){
			this.sectionID = sectionID;
		}

		@Override
		public void run() {

			Collection<Item> list = new ArrayList<Item>();
			String path = "?section_id=" + sectionID;

			JSONObject json = wimpl.invokeRESTAPI(HTTP_METHOD.GET, Path.ITEM_FREQUENT + path, "");
			if(null == json){
				Log.e(LOG_TAG, "[Frequent Item] Error response - null returned");
				wimpl.sm(CommandID.CMD_GET_FRQUENT_ITEMS, 0, 0, list);
				return;
			}

			if(false == json.get("code").toString().startsWith("2")){
				Log.e(LOG_TAG, "[Frequent Item] Error response - " + json.get("message").toString());
				wimpl.sm(CommandID.CMD_GET_FRQUENT_ITEMS, 0, 0, list);
				return;
			}

			wimpl.setRemainedAPICall(json.get("rest_of_api").toString());
			JSONObject results = (JSONObject) json.get("results");
			for(Object type : results.keySet()){

				JSONArray rows  = (JSONArray) results.get(type);
				for(int i = 0; i < rows.size(); i++){
					JSONObject row = (JSONObject) rows.get(i);

					list.add(new Item(row));
				}
			}
			Log.d(LOG_TAG, "[Frequent Item] Providing Frequent Items");
			wimpl.sm(CommandID.CMD_GET_FRQUENT_ITEMS, 1, 0, list);
		}			

	}

	
	public boolean getLatestItems(String sectionID, boolean forceUpdate){

		new GetLatestItemsTaskThread(sectionID, forceUpdate).start();		
		return true;
	}
	
	private class GetLatestItemsTaskThread extends Thread{

		final String sectionID;
		final boolean forceUpdate;

		GetLatestItemsTaskThread(String sectionID, boolean forceUpdate){
			this.sectionID = sectionID;
			this.forceUpdate = forceUpdate;
		}

		@Override
		public void run() {

			if(false == forceUpdate &&
					null != wimpl.getLatestItemDBHandler() &&
					wimpl.getLatestItemDBHandler().hasData() ){
				Log.d(LOG_TAG, "[Latest Item] Providing GetLatestItems from Cache!!!");
				wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 1, 0, wimpl.getLatestItemDBHandler().getAllItems());
				return;
			}
			
			Collection<Item> list = new ArrayList<Item>();
			if(null == sectionID ||
					sectionID.isEmpty()){
				Log.e(LOG_TAG, "[Latest Item] Failed - invalid sectionID !!!");
				wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 0, 0, list);
				return;
			}
			
			String path = "?section_id=" + sectionID;

			JSONObject json = null; 
			json = wimpl.invokeRESTAPI(HTTP_METHOD.GET, Path.ITEM_LATEST + path, "");

			if(null == json){
				Log.e(LOG_TAG, "[Latest Item] Error response - null returned");
				wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 0, 0, list);
				return;
			}

			if(false == json.get("code").toString().startsWith("2")){
				Log.d(LOG_TAG, "[Latest Item] Failed - GetLatestItems from Server!!!" + json.get("message").toString());
				wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 0, 0, list);
				return;
			}

			wimpl.setRemainedAPICall(json.get("rest_of_api").toString());
			JSONArray results = (JSONArray) json.get("results");
			for(int i = 0; i < results.size(); i++){
				JSONObject row = (JSONObject) results.get(i);

				// to hide unnecessary entries
				if(row.get("item").toString().startsWith("Adjusted to close")){
					continue;
				}
				
				list.add(new Item(row));
			}
			wimpl.getLatestItemDBHandler().insert(list);
			Log.d(LOG_TAG, "[Latest Item] Providing GetLatestItems from Server!!!");
			wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 1, 0, list);
		}			

	}

	
	public boolean getMonthlyItems(String sectionID, boolean forceUpdate){

		new GetMonthlyItemsTaskThread(sectionID, forceUpdate).start();		
		return true;
	}

	private class GetMonthlyItemsTaskThread extends Thread{

		final String sectionID;
		final boolean forceUpdate;

		GetMonthlyItemsTaskThread(String sectionID, boolean forceUpdate){
			this.sectionID = sectionID;
			this.forceUpdate = forceUpdate;
		}

		@Override
		public void run() {

			if(false == forceUpdate &&
					null != wimpl.getMonthlyItemDBHandler() &&
					wimpl.getMonthlyItemDBHandler().hasData() ){
				Log.d(LOG_TAG, "[Monthly Item] Providing GetMonthlyItem from Cache!!!");
				wimpl.sm(CommandID.CMD_GET_MONTHLY_ITEMS, 1, 0, wimpl.getMonthlyItemDBHandler().getAllItems());
				return;
			}
			
			ArrayList<Item> list = new ArrayList<Item>();
			String path = "?section_id=" + sectionID;

			JSONObject json = wimpl.invokeRESTAPI(HTTP_METHOD.GET, Path.ITEM_MONTHLY + path, "");
			if(null == json){
				Log.e(LOG_TAG, "[Monthly Item] Error response - null returned");
				wimpl.sm(CommandID.CMD_GET_MONTHLY_ITEMS, 0, 0, list);
				return;
			}

			if(false == json.get("code").toString().startsWith("2")){
				Log.e(LOG_TAG, "[Monthly Item] Error response - " + json.get("message").toString());
				wimpl.sm(CommandID.CMD_GET_MONTHLY_ITEMS, 0, 0, list);
				return;
			}

			wimpl.setRemainedAPICall(json.get("rest_of_api").toString());
			
			JSONObject results = null;
			try{
				results = (JSONObject) json.get("results");	
			}catch(Exception e){
				Log.d(LOG_TAG, "[Monthly Item] Nothing...");
				wimpl.sm(CommandID.CMD_GET_MONTHLY_ITEMS, 1, 0, list);
				return;
			}
			
			for(Object type : results.keySet()){

				if(false == type.toString().startsWith("slot")){
					continue;
				}
				
				JSONArray rows  = (JSONArray) results.get(type);
				for(int i = 0; i < rows.size(); i++){
					JSONObject row = (JSONObject) rows.get(i);

					list.add(new Item(row));
				}
			}
			wimpl.getMonthlyItemDBHandler().clean();
			wimpl.getMonthlyItemDBHandler().insert(list);
			Log.d(LOG_TAG, "[Monthly Item] Providing Frequent Items");
			wimpl.sm(CommandID.CMD_GET_MONTHLY_ITEMS, 1, 0, list);
		}			

	}

	
	public boolean removeMonthlyItem(String sectionID, String itemID){

		new DeleteMonthlyItemTaskThread(sectionID, itemID).start();		
		return true;
	}

	private class DeleteMonthlyItemTaskThread extends Thread{

		final String sectionID;
		final String itemID;

		DeleteMonthlyItemTaskThread(String sectionID, String itemID){
			this.sectionID = sectionID;
			this.itemID = itemID;
		}

		@Override
		public void run() {

			JSONObject json = wimpl.invokeRESTAPI(HTTP_METHOD.DELETE, Path.ITEM_MONTHLY_REMOVE + itemID + "/" + sectionID + ".json_array", "");

			if(null == json){
				Log.e(LOG_TAG, "[DeleteMonthItem] Error response - null returned");
				wimpl.sm(CommandID.CMD_DELETE_MONTHLY_ITEMS, 0, 0, "");
				return;
			}

			if(false == json.get("code").toString().startsWith("2")){
				Log.e(LOG_TAG, "[DeleteMonthItem] Error response -  - " + json.get("message").toString());
				wimpl.sm(CommandID.CMD_DELETE_MONTHLY_ITEMS, 0, 0, "");
				return;
			}

			Log.d(LOG_TAG, "[DeleteMonthItem] Providing response");
			wimpl.getMonthlyItemDBHandler().remove(itemID);
			wimpl.sm(CommandID.CMD_DELETE_MONTHLY_ITEMS, 1, 0, itemID);
		}			

	}

}
