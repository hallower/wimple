package me.blog.imhallower.wimple.impl.db;

import java.util.ArrayList;
import java.util.Collection;

import me.blog.imhallower.wimple.model.Item;
import android.content.Context;

public class ItemDBHandler {
	
    private static String tableName = "iteminfo";
    private static String createSchema = "CREATE TABLE IF NOT EXISTS " + tableName + "(" +
            			
            "id TEXT, " +
            "date TEXT, " +
			"leftAccount TEXT, " +
			"leftAccountID TEXT, " +
			"rightAccount TEXT, " +
			"rightAccountID TEXT, " +
			"item TEXT, " +
			"amount TEXT, " +

			"PRIMARY KEY (id, date, item)" +
            ") ";
    
    private static DatabaseHandler dbHandler = null;

    public ItemDBHandler(Context context) {
        super();

        dbHandler = new DatabaseHandler(context, createSchema, tableName);        
        dbHandler.setColumns(Item.columns);
    }

    public boolean insert(Item data) {
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
        
    public boolean insert(Collection<Item> data) {
        boolean res = false;
      
        if(data==null || data.isEmpty()){
            return false;
        }
        
        // TODO : use TCL
        for(Item act : (Collection<Item>)data) {
            
            if(act instanceof IDatabaseRecord){
                dbHandler.addItem(act);
            }
        }
        //dbHandler.showAll();
        return res;
    }

    /*
    private Collection<Item> get(String where){
    	Collection<Item> acts = new ArrayList<Item>();
    	Collection<IDatabaseRecord> records = dbHandler.getItems(where);

    	Log.d(tableName, where);

    	for(IDatabaseRecord record : records){
    		Item data = new Item();

    		if(false == data.setValues(record.getValues())){
    			continue;
    		}
    		acts.add(data);
    	}

    	dbHandler.showAll();
    	return acts;
    }
    */

    public Collection<Item> getAllItems(){
    	Collection<Item> acts = new ArrayList<Item>();
    	Collection<IDatabaseRecord> records = dbHandler.getItems();

    	for(IDatabaseRecord record : records){
    		Item data = new Item();

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
