package kr.blogspot.charlie0301.wimple.impl.db;

import java.util.ArrayList;
import java.util.Collection;

import kr.blogspot.charlie0301.wimple.model.Section;

import android.content.Context;

public class SectionDBHandler {

	private static String tableName = "sectioninfo";
	private static String createSchema = "CREATE TABLE IF NOT EXISTS " + tableName + "(" +

			"id TEXT, " +
			"title TEXT," +
			"description TEXT, " +
			"currency TEXT, " +
			"isolation TEXT, " +
			"asset TEXT, " +
			"debt TEXT, " +
			"skinid TEXT, " +
			"decimalposition TEXT, " +
			"dateformat TEXT, " +

			"PRIMARY KEY (id)" +
			") ";

	private static DatabaseHandler dbHandler = null;

	public SectionDBHandler(Context context) {
		super();

		dbHandler = new DatabaseHandler(context, createSchema, tableName);        
		dbHandler.setColumns(Section.columns);
	}

	public boolean insert(Section data) {
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

	public boolean insert(Collection<Section> data) {
		boolean res = false;

		if(data==null || data.isEmpty()){
			return false;
		}

		// TODO : use TCL
		for(Section act : (Collection<Section>)data) {

			if(act instanceof IDatabaseRecord){
				dbHandler.addItem(act);
			}
		}
		//dbHandler.showAll();
		return res;
	}

	/*
    private Collection<Section> get(String where){
    	Collection<Section> acts = new ArrayList<Section>();
    	Collection<IDatabaseRecord> records = dbHandler.getItems(where);

    	Log.d(tableName, where);

    	for(IDatabaseRecord record : records){
    		Section data = new Section();

    		if(false == data.setValues(record.getValues())){
    			continue;
    		}
    		acts.add(data);
    	}

    	dbHandler.showAll();
    	return acts;
    }
	 */

	public Collection<Section> getAllSections(){
		Collection<Section> acts = new ArrayList<Section>();
		Collection<IDatabaseRecord> records = dbHandler.getItems();

		for(IDatabaseRecord record : records){
			Section data = new Section();

			if(false == data.setValues(record.getValues())){
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
