package kr.blogspot.charlie0301.wimple.impl.db;

import java.util.ArrayList;
import java.util.Collection;

import kr.blogspot.charlie0301.wimple.model.AccountState;
import android.content.Context;

public class AccountStateDBHandler {

	private static String tableName = "accountstate";
	private static String createSchema = "CREATE TABLE IF NOT EXISTS " + tableName + "(" +

			"accountid TEXT, " +
			"accountname TEXT, " +
			"category TEXT, " +
			"amount TEXT, " +
			"seq TEXT, " +
			"group_ TEXT, " +
			
			"PRIMARY KEY (accountid)" +
			") ";

	private static DatabaseHandler dbHandler = null;

	public AccountStateDBHandler(Context context) {
		super();

		dbHandler = new DatabaseHandler(context, createSchema, tableName);        
		dbHandler.setColumns(AccountState.columns);
	}

	public boolean insert(AccountState data) {
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

	public boolean insert(Collection<AccountState> data) {
		boolean res = false;

		if(data==null || data.isEmpty()){
			return false;
		}

		// TODO : use TCL
		for(AccountState act : data) {

			if(act instanceof IDatabaseRecord){
				dbHandler.addItem(act);
			}
		}
		dbHandler.showAll();
		return res;
	}

	/*
    private Collection<AccountState> get(String where){
    	Collection<AccountState> acts = new ArrayList<AccountState>();
    	Collection<IDatabaseRecord> records = dbHandler.getItems(where);

    	Log.d(tableName, where);

    	for(IDatabaseRecord record : records){
    		AccountState data = new AccountState();

    		if(!data.setValues(record.getValues())){
    			continue;
    		}
    		acts.add(data);
    	}

    	dbHandler.showAll();
    	return acts;
    }
	 */

	public Collection<AccountState> getAllAccountStates(){
		Collection<AccountState> acts = new ArrayList<>();
		Collection<IDatabaseRecord> records = dbHandler.getItems();

		for(IDatabaseRecord record : records){
			AccountState data = new AccountState();

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
