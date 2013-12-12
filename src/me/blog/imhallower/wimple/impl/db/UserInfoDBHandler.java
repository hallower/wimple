package me.blog.imhallower.wimple.impl.db;

import java.util.Collection;

import me.blog.imhallower.wimple.impl.WimpleImpl;
import me.blog.imhallower.wimple.model.UserInfo;
import android.content.Context;


public class UserInfoDBHandler {

	/*   
	 * 	columns.append(0, "id");
	 * 	columns.append(1,  "name");
	 * 	columns.append(2,  "join_date");
	 * 	columns.append(3,  "profile_image_url");
	 * 	columns.append(4,  "mileage");
	 */

	private static String LOG_TAG = "UserInfoDBHandler";
	private static String tableName = "userinfo";
	private static String createSchema = "CREATE TABLE IF NOT EXISTS " + tableName + "(" +
			"id TEXT PRIMARY KEY, " +
			"name TEXT, " +
			"join_date TEXT, " +
			"profile_image_url TEXT, " +
			"mileage TEXT " + 
			") ";

	private static DatabaseHandler dbHandler = null;

	public UserInfoDBHandler(Context context) {
		super();

		dbHandler = new DatabaseHandler(context, createSchema, tableName);        
		dbHandler.setColumns(UserInfo.columns);
		//Log.d(LOG_TAG, "Database Hander :" + LOG_TAG + " is created!!!");
	}



	public boolean cleanAndInsert(Collection<UserInfo> sessions) {
		boolean res = false;

		dbHandler.deleteAll();

		if(sessions.isEmpty()){
			return true;
		}

		dbHandler.addItems(sessions);
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

		dbHandler.showAll();
		return info;
	}

}
