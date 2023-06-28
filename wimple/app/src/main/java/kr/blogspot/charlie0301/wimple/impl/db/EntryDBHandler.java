package kr.blogspot.charlie0301.wimple.impl.db;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collection;

import kr.blogspot.charlie0301.wimple.model.Entry;

public class EntryDBHandler {

    private static final String tableName = "entryinfo";
    private static final String createSchema = "CREATE TABLE IF NOT EXISTS " + tableName + "(" +

            "id TEXT, " +
            "date TEXT, " +
            "leftAccount TEXT, " +
            "leftAccountID TEXT, " +
            "rightAccount TEXT, " +
            "rightAccountID TEXT, " +
            "item TEXT, " +
            "amount TEXT, " +
            "balance TEXT, " +
            "memo TEXT, " +
            "appID TEXT, " +
            "dateValue TEXT, " +

            "PRIMARY KEY (id)" +
            ") ";

    private static DatabaseHandler dbHandler = null;

    public EntryDBHandler(Context context) {
        super();

        dbHandler = new DatabaseHandler(context, createSchema, tableName);
        dbHandler.setColumns(Entry.columns);
    }

    public boolean insert(Entry data) {
        if (data != null) {
            return dbHandler.addItem(data);
        }
        dbHandler.showAll();
        return false;
    }

    public boolean hasData() {
        return dbHandler.getCountAll() > 0;
    }

    public void clean() {
        dbHandler.deleteAll();
    }

    public void cleanOldEntries(Long date) {
        //String dateString = DateFormatUtils.getDBDateFormat().format(date);
        //dbHandler.delete("date < " + dateString);
        dbHandler.delete("date < " + date);
    }

    public void remove(String id) {
        dbHandler.delete("id = " + id);
    }

    public boolean insert(Collection<Entry> data) {

        if (data == null || data.isEmpty()) {
            return false;
        }

        // TODO : use TCL
        for (Entry act : data) {

            if (act != null) {
                dbHandler.addItem(act);
            }
        }
        //dbHandler.showAll();
        return true;
    }

	/*
    private Collection<Entry> get(String where){
    	Collection<Entry> acts = new ArrayList<Entry>();
    	Collection<IDatabaseRecord> records = dbHandler.getItems(where);

    	Log.d(tableName, where);

    	for(IDatabaseRecord record : records){
    		Entry data = new Entry();

    		if(!data.setValues(record.getValues())){
    			continue;
    		}
    		acts.add(data);
    	}

    	dbHandler.showAll();
    	return acts;
    }
	 */

    public Entry getEntry(String id) {
        IDatabaseRecord record = dbHandler.getItem("id", id);

        if (null == record) {
            return null;
        }

        Entry entry = new Entry();
        entry.setValues(record.getValues());

        return entry;
    }

    public Collection<Entry> getAllEntrys() {
        Collection<Entry> acts = new ArrayList<>();
        Collection<IDatabaseRecord> records = dbHandler.getItems();

        for (IDatabaseRecord record : records) {
            Entry data = new Entry();

            if (!data.setValues(record.getValues())) {
                continue;
            }
            acts.add(data);
        }

        dbHandler.showAll();
        return acts;
    }


    public void print() {
        dbHandler.showAll();
    }

}
