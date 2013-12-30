package me.blog.imhallower.wimple.impl;

import java.util.ArrayList;
import java.util.Collection;

import me.blog.imhallower.wimple.impl.RestAPIInvoker.HTTP_METHOD;
import me.blog.imhallower.wimple.impl.WimpleImpl.CommandID;
import me.blog.imhallower.wimple.impl.WimpleImpl.Path;
import me.blog.imhallower.wimple.model.Entry;
import me.blog.imhallower.wimple.model.Item;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

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
				wimpl.sm(CommandID.CMD_GET_FRQUENT_ITEMS, 0, 0, list);
				return;
			}

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

	
	public boolean getLatestItems(String sectionID){

		new GetLatestItemsTaskThread(sectionID).start();		
		return true;
	}
	
	private class GetLatestItemsTaskThread extends Thread{

		final String sectionID;

		GetLatestItemsTaskThread(String sectionID){
			this.sectionID = sectionID;
		}

		@Override
		public void run() {

			Collection<Item> list = new ArrayList<Item>();
			String path = "?section_id=" + sectionID;

			JSONObject json = null; 
			json = wimpl.invokeRESTAPI(HTTP_METHOD.GET, Path.ITEM_LATEST + path, "");

			if(null == json){
				wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 0, 0, list);
				return;
			}

			JSONArray results = (JSONArray) json.get("results");
			for(int i = 0; i < results.size(); i++){
				JSONObject row = (JSONObject) results.get(i);

				list.add(new Item(row));
			}
			wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 1, 0, list);
		}			

	}

}
