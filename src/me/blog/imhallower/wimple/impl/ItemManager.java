package me.blog.imhallower.wimple.impl;

import java.util.ArrayList;
import java.util.Collection;

import me.blog.imhallower.wimple.impl.RestAPIInvoker.HTTP_METHOD;
import me.blog.imhallower.wimple.impl.WimpleImpl.CommandID;
import me.blog.imhallower.wimple.impl.WimpleImpl.Path;
import me.blog.imhallower.wimple.model.Item;

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
			if(null == json ||
					false == json.get("code").toString().startsWith("2")){
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
					wimpl.getIDBHandler().hasData() ){
				Log.d(LOG_TAG, "[Latest Item] Providing GetLatestItems from Cache!!!");
				wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 1, 0, wimpl.getIDBHandler().getAllItems());
				return;
			}
			
			Collection<Item> list = new ArrayList<Item>();
			if(null == sectionID ||
					sectionID.isEmpty()){
				Log.d(LOG_TAG, "[Latest Item] Failed - invalid sectionID !!!");
				wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 0, 0, list);
				return;
			}
			
			String path = "?section_id=" + sectionID;

			JSONObject json = null; 
			json = wimpl.invokeRESTAPI(HTTP_METHOD.GET, Path.ITEM_LATEST + path, "");

			if(null == json ||
					false == json.get("code").toString().startsWith("2")){
				Log.d(LOG_TAG, "[Latest Item] Failed - GetLatestItems from Server!!!");
				wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 0, 0, list);
				return;
			}

			wimpl.setRemainedAPICall(json.get("rest_of_api").toString());
			JSONArray results = (JSONArray) json.get("results");
			for(int i = 0; i < results.size(); i++){
				JSONObject row = (JSONObject) results.get(i);

				list.add(new Item(row));
			}
			wimpl.getIDBHandler().insert(list);
			Log.d(LOG_TAG, "[Latest Item] Providing GetLatestItems from Server!!!");
			wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 1, 0, list);
		}			

	}

}
