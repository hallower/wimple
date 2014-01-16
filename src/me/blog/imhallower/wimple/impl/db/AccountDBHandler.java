package me.blog.imhallower.wimple.impl.db;

import java.util.ArrayList;
import java.util.Collection;

import me.blog.imhallower.wimple.model.Account;
import android.content.Context;
import android.util.Log;

public class AccountDBHandler {

    private static String tableName = "accountinfo";
    private static String createSchema = "CREATE TABLE IF NOT EXISTS " + tableName + "(" +
            
			"what TEXT, " +
            "id TEXT, " +
            "type TEXT, " +
			"title TEXT, " +
			"description TEXT, " +
			"openedDate TEXT, " +
			"closedDate TEXT, " +
			"category TEXT, " +
			"useDate TEXT, " +
			"payDate TEXT, " +
			"payAccount TEXT, " +

			"PRIMARY KEY (id)" +
            ") ";
    
    private static DatabaseHandler dbHandler = null;

    public AccountDBHandler(Context context) {
        super();

        dbHandler = new DatabaseHandler(context, createSchema, tableName);        
        dbHandler.setColumns(Account.columns);
    }

    public boolean insert(Account data) {
        if(data instanceof IDatabaseRecord){
            return dbHandler.addItem(data);
        }
        dbHandler.showAll();
        return false;
    }
    
    public boolean hasData() {
    	return dbHandler.getCountAll() > 0;
    }
    
    public void clean(){
    	//dbHandler.delete("user_id", ee.getInstance().getProfileID());
    	dbHandler.deleteAll();
    }
        
    public boolean insert(Collection<Account> data) {
        boolean res = false;
      
        if(data==null || data.isEmpty()){
            return false;
        }
        
        // TODO : use TCL
        for(Account act : (Collection<Account>)data) {
            
            if(act instanceof IDatabaseRecord){
                dbHandler.addItem(act);
            }
        }
        //dbHandler.showAll();
        return res;
    }

    private Collection<Account> get(String where){
    	Collection<Account> acts = new ArrayList<Account>();
    	Collection<IDatabaseRecord> records = dbHandler.getItems(where);

    	Log.d(tableName, where);

    	for(IDatabaseRecord record : records){
    		Account data = new Account();

    		if(false == data.setValues(record.getValues())){
    			continue;
    		}
    		acts.add(data);
    	}

    	dbHandler.showAll();
    	return acts;
    }

    public Collection<Account> getAllAccounts(){
    	Collection<Account> acts = new ArrayList<Account>();
    	Collection<IDatabaseRecord> records = dbHandler.getItems();

    	for(IDatabaseRecord record : records){
    		Account data = new Account();

    		if(false == data.setValues(record.getValues())){
    			continue;
    		}
    		acts.add(data);
    	}

    	dbHandler.showAll();
    	return acts;
    }
       
    
    public void print(){
    	dbHandler.showAll();
    }

}
