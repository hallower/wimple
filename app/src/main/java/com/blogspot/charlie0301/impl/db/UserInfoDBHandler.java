package com.blogspot.charlie0301.impl.db;

import java.util.Collection;

import com.blogspot.charlie0301.impl.WimpleImpl;
import com.blogspot.charlie0301.model.UserInfo;

import android.content.Context;


public class UserInfoDBHandler {

	/*   
	 * 	columns.append(0, "id");
	 * 	columns.append(1,  "name");
	 * 	columns.append(2,  "join_date");
	 * 	columns.append(3,  "profile_image_url");
	 * 	columns.append(4,  "mileage");
	 *  columns.append(5,  "api_count_level");
	 */

	//private static String LOG_TAG = "UserInfoDBHandler";
	private static String tableName = "userinfo";
	private static String createSchema = "CREATE TABLE IF NOT EXISTS " + tableName + "(" +
			"id TEXT PRIMARY KEY, " +
			"name TEXT, " +
			"join_date TEXT, " +
			"profile_image_url TEXT, " +
			"mileage TEXT, " + 
			"api_count_level TEXT " +
			") ";

	private static DatabaseHandler dbHandler = null;

	public UserInfoDBHandler(Context context) {
		super();

		dbHandler = new DatabaseHandler(context, createSchema, tableName);        
		dbHandler.setColumns(UserInfo.columns);
		//Log.d(LOG_TAG, "Database Hander :" + LOG_TAG + " is created!!!");
	}

	public void clean(){
		dbHandler.deleteAll();
	}

	public boolean cleanAndInsert(UserInfo userinfo) {
		boolean res = false;

		dbHandler.deleteAll();
		dbHandler.addItem(userinfo);
		dbHandler.showAll();
		return res;
	}

	public UserInfo get(){
		UserInfo info = new UserInfo();

		Collection<IDatabaseRecord> records = dbHandler.getItems("id", WimpleImpl.getInstance().getUserID());

		for(IDatabaseRecord record : records){

			// TODO : Have to consider how to get the userinfo records
			//         Also, I should have make a policy for the multiple accounts
			info.setValues(record.getValues());
		}

		//dbHandler.showAll();
		return info;
	}


	public boolean hasData() {
		return dbHandler.getCount("id", WimpleImpl.getInstance().getUserID()) > 0;
	}


}
