package kr.blogspot.charlie0301.wimple.impl.db;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import kr.blogspot.charlie0301.wimple.model.Budget;
import android.content.Context;

public class BudgetDBHandler {
	
	private static String tableName = "budget";
	private static String createSchema = "CREATE TABLE IF NOT EXISTS " + tableName + "(" +

			"accountid TEXT, " +
			"budget TEXT, " +
			"current TEXT, " +
			"remains TEXT, " +
			"type TEXT, " +

			"PRIMARY KEY (accountid, type)" +
			") ";

	private static DatabaseHandler dbHandler = null;

	public BudgetDBHandler(Context context) {
		super();

		dbHandler = new DatabaseHandler(context, createSchema, tableName);        
		dbHandler.setColumns(Budget.columns);
	}

	public boolean insert(Budget data) {
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

	public boolean insert(Map<String, Budget> data) {
		boolean res = false;

		if(data==null || data.isEmpty()){
			return false;
		}

		// TODO : use TCL
		for(String key : data.keySet()) {

			Budget act = data.get(key);
			if(act instanceof IDatabaseRecord){
				dbHandler.addItem(act);
			}
		}
		dbHandler.showAll();
		return res;
	}

	/*
    private Collection<Budget> get(String where){
    	Collection<Budget> acts = new ArrayList<Budget>();
    	Collection<IDatabaseRecord> records = dbHandler.getItems(where);

    	Log.d(tableName, where);

    	for(IDatabaseRecord record : records){
    		Budget data = new Budget();

    		if(!data.setValues(record.getValues())){
    			continue;
    		}
    		acts.add(data);
    	}

    	dbHandler.showAll();
    	return acts;
    }
	 */

	public Collection<Budget> getAllBudgets(boolean isIncome){
		Collection<Budget> acts = new ArrayList<>();
		Collection<IDatabaseRecord> records = dbHandler.getItems("type", isIncome?"income":"expense");

		for(IDatabaseRecord record : records){
			Budget data = new Budget();
			
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
