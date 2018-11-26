package kr.blogspot.charlie0301.wimple.impl.db;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collection;

import kr.blogspot.charlie0301.wimple.model.Account;

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
            "seq TEXT, " +

            "PRIMARY KEY (id)" +
            ") ";

    private static DatabaseHandler dbHandler = null;

    public AccountDBHandler(Context context) {
        super();

        dbHandler = new DatabaseHandler(context, createSchema, tableName);
        dbHandler.setColumns(Account.columns);
    }

    public boolean insert(Account data) {
        if (data != null) {
            return dbHandler.addItem(data);
        }
        //dbHandler.showAll();
        return false;
    }

    public boolean hasData() {
        return dbHandler.getCountAll() > 0;
    }

    public void clean() {
        //dbHandler.delete("user_id", ee.getInstance().getProfileID());
        dbHandler.deleteAll();
    }

    public boolean insert(Collection<Account> data) {

        if (data == null || data.isEmpty()) {
            return false;
        }

        // TODO : use TCL
        for (Account act : data) {

            if (act != null) {
                dbHandler.addItem(act);
            }
        }
        //dbHandler.showAll();
        return true;
    }

	/*
    private Collection<Account> get(String where){
    	Collection<Account> acts = new ArrayList<Account>();
    	Collection<IDatabaseRecord> records = dbHandler.getItems(where);

    	Log.d(tableName, where);

    	for(IDatabaseRecord record : records){
    		Account data = new Account();

    		if(!data.setValues(record.getValues())){
    			continue;
    		}
    		acts.add(data);
    	}

    	dbHandler.showAll();
    	return acts;
    }
	 */

    public Collection<Account> getAllAccounts() {
        Collection<Account> acts = new ArrayList<>();
        Collection<IDatabaseRecord> records = dbHandler.getItems();

        for (IDatabaseRecord record : records) {
            Account data = new Account();

            if (!data.setValues(record.getValues())) {
                continue;
            }
            acts.add(data);
        }

        //dbHandler.showAll();
        return acts;
    }

    public Account getAccountName(String accountID) {

        try {
            IDatabaseRecord record = dbHandler.getItem("id", accountID);
            Account account = new Account();

            if (!account.setValues(record.getValues())) {
                return null;
            }

            return account;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void print() {
        dbHandler.showAll();
    }

}
