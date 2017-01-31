package kr.blogspot.charlie0301.wimple.impl.db;

import java.util.ArrayList;
import java.util.Collection;

import kr.blogspot.charlie0301.wimple.model.Item;
import android.content.Context;

public class ItemDBHandler {
	
    private String tableName = "iteminfo";
    private static String createSchema = "CREATE TABLE IF NOT EXISTS " + "%s" + "(" +
            			
            "id TEXT, " +
            "date TEXT, " +
			"leftAccount TEXT, " +
			"leftAccountID TEXT, " +
			"rightAccount TEXT, " +
			"rightAccountID TEXT, " +
			"item TEXT, " +
			"amount TEXT, " +
			"dateValue TEXT, " +

			"PRIMARY KEY (id, date, item)" +
            ") ";
    
    private DatabaseHandler dbHandler = null;

    public ItemDBHandler(Context context, String tableName) {
        super();

        this.tableName = tableName;
        dbHandler = new DatabaseHandler(context, String.format(createSchema, this.tableName), this.tableName);        
        dbHandler.setColumns(Item.columns);
    }

    public boolean insert(Item data) {
        if(data instanceof IDatabaseRecord){
            return dbHandler.addItem(data);
        }
        //dbHandler.showAll();
        return false;
    }
    
    public boolean hasData() {
    	return dbHandler.getCountAll() > 0;
    }
    
    public void clean(){
    	//dbHandler.delete("user_id", ee.getInstance().getProfileID());
    	dbHandler.deleteAll();
    }
    
    public void remove(String id){		
		dbHandler.delete("id", id);
	}
    
    public boolean insert(Collection<Item> data) {
        boolean res = false;
      
        if(data==null || data.isEmpty()){
            return false;
        }
        
        // TODO : use TCL
        for(Item act : data) {
            
            if(act instanceof IDatabaseRecord){
                dbHandler.addItem(act);
            }
        }
        //dbHandler.showAll();
        return res;
    }

    public Item getItem(String id){
		IDatabaseRecord record = dbHandler.getItem("id", id);
		
		if(null == record){
			return null;
		}
		
		Item item = new Item();
		item.setValues(record.getValues());
		
		return item;
	}
    
    
    public Collection<Item> getAllItems(){
    	Collection<Item> acts = new ArrayList<Item>();
    	Collection<IDatabaseRecord> records = dbHandler.getItems();

    	for(IDatabaseRecord record : records){
    		Item data = new Item();

    		if(!data.setValues(record.getValues())){
    			continue;
    		}
    		acts.add(data);
    	}

    	//dbHandler.showAll();
    	return acts;
    }
       
    
    public void print(){
    	dbHandler.showAll();
    }

}
