package me.blog.imhallower.wimple.impl.db;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

import android.content.Context;
import android.util.Log;

public class ActInfoDBHandler {
/*
    private static String tableName = "actinfo";
    private static String createSchema = "CREATE TABLE IF NOT EXISTS " + tableName + "(" +
            
            "feed_type INTEGER, " +
            
			"title TEXT, " +
            "subtitle TEXT, " +
            "time INTEGER, " +
            "act_user_id TEXT, " +
            
			"promise_id TEXT, " +
			"promise_title TEXT, " +
			
			"act_issuer_name TEXT, " +
			"act_receiver_name TEXT, " +
			"act_description TEXT, " +
			"act_count INTEGER, " +
			
			"ref_title TEXT, " +
			"ref_subtitle TEXT, " +
			"ref_time INTEGER, " +
			"ref_user_id TEXT, " +

			"effect_id TEXT, " +
			"points INTEGER, " +
			"stamp_type INTEGER, " +
			
			"actor_id TEXT, " +
			"object_id TEXT, " +
			"user_id TEXT, " +

			"PRIMARY KEY (time,title)" +
            ") ";
    
    private static DatabaseHandler dbHandler = null;

    public ActInfoDBHandler(Context context) {
        super();

        dbHandler = new DatabaseHandler(context, createSchema, tableName);        
        dbHandler.setColumns(ActInfo.columns);
    }

    public boolean insert(ActInfo data) {
        if(data instanceof IDatabaseRecord){
            return dbHandler.addItem(data);
        }
        dbHandler.showAll();
        return false;
    }
    
    public boolean hasData(Long latest, Long oldest) {
    	return dbHandler.getCount("user_id='"+Promise.getInstance().getProfileID()
    			+"' AND time < " + latest + " AND time > " + oldest)>0;
    }
    
    public Long getLatestDate(){
    	String query = "select max(time) as result from " + tableName;
    	long result = dbHandler.getValue(query, "result");
    	if(result < 0){
    		result = Calendar.getInstance().getTimeInMillis();
    		result += 24 * 60 * 60 * 1000;
    	}
    	return result;
    }

    public void clean(){
    	dbHandler.delete("user_id", Promise.getInstance().getProfileID());
    }
        
    public boolean insert(Collection<ActInfo> acts) {
        boolean res = false;
      
        if(acts==null || acts.isEmpty()){
            return false;
        }
        
        // TODO : use TCL
        for(ActInfo act : (Collection<ActInfo>)acts) {
            
            if(act instanceof IDatabaseRecord){
                dbHandler.addItem(act);
            }
        }
        //dbHandler.showAll();
        return res;
    }

    private Collection<ActInfo> get(String where){
    	Collection<ActInfo> acts = new ArrayList<ActInfo>();
    	Collection<IDatabaseRecord> records = dbHandler.getItems(where);

    	Log.d(tableName, where);

    	for(IDatabaseRecord record : records){
    		ActInfo data = new ActInfo(FEED_TYPE.valueOf(record.getValue(0)));

    		if(false == data.setValues(record.getValues())){
    			continue;
    		}
    		acts.add(data);
    	}

    	dbHandler.showAll();
    	return acts;
    }

    public Collection<ActInfo> getAllActLogs(Long latest, Long oldest, int count){
    	String query = "";
    	String id = Promise.getInstance().getProfileID();
    	
    	if(oldest <= 0){
    		query = "user_id = '" + id + "' AND time < " + latest.toString() + " ORDER BY time DESC LIMIT " + count;
    	}else{
    		query = "user_id = '" + id + "' AND time > " + oldest.toString() + " AND time < " + latest.toString() + " ORDER BY time DESC ";	
    	}
    	return get(query);
    }
    
    public Collection<ActInfo> getMyActLogs(Long latest, Long oldest, int count){
    	String query = "";
    	String id = Promise.getInstance().getProfileID();
    	
    	if(oldest <= 0){
    		query = "(actor_id = '" + id + "' OR object_id = '" + id + "') AND user_id='"+ id + "' AND time < " + latest.toString() + " ORDER BY time DESC LIMIT " + count;
    	}else{
    		query = "(actor_id = '" + id + "' OR object_id = '" + id + "') AND user_id='"+ id + "' AND time > " + oldest.toString() + " AND time < " + latest.toString() + " ORDER BY time DESC ";	
    	}
    	return get(query);
    }
    
    public Collection<ActInfo> getAllActLogsInPromise(String promiseID, Long latest, Long oldest, int count){
    	String query = "";
    	String id = Promise.getInstance().getProfileID();
    	
    	if(oldest <= 0){
    		query = "promise_id = '" + promiseID + "' AND user_id='"+ id + "' AND time < " + latest.toString() + " ORDER BY time DESC LIMIT " + count;
    	}else{
    		query = "promise_id = '" + promiseID + "' AND user_id='"+ id + "' AND time > " + oldest.toString() + " AND time < " + latest.toString() + " ORDER BY time DESC ";	
    	}
    	return get(query);
    }
    
    public Collection<ActInfo> getMyActLogsInPromise(String promiseID, Long latest, Long oldest, int count){
    	String query = "";
    	String id = Promise.getInstance().getProfileID();
    	
    	if(oldest <= 0){
    		query = "(actor_id = '" + id + "' OR object_id = '" + id + "') AND promise_id = '" + promiseID + "' AND user_id='"+ id + "' AND time < " + latest.toString() + " ORDER BY time DESC LIMIT " + count;
    	}else{
    		query = "(actor_id = '" + id + "' OR object_id = '" + id + "') AND promise_id = '" + promiseID + "' AND user_id='"+ id + "' AND time > " + oldest.toString() + " AND time < " + latest.toString() + " ORDER BY time DESC ";	
    	}
    	return get(query);
    }
    
    
    public void print(){
    	dbHandler.showAll();
    }
    */
}
