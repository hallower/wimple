package kr.blogspot.charlie0301.wimple.impl.db;

import java.util.ArrayList;
import java.util.Collection;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.util.SparseArray;

/*
 * DatabaseHandler
 * The class provides content managing interfaces of the SQLite Database
 */
class DatabaseHandler{

	private static final String LOG_TAG = "DatabaseHandler";	

	private static final boolean NEEDTOREMOVEALLTABLES = false;
	private static final boolean DEBUGALLDATAS = false;

	private static PromiseDatabase dbms = null;	
	private final String createSchema;    
	private final String tableName;

	private SparseArray<String> columns = new SparseArray<>();

	/*
	 * PromiseDatabase
	 * The class manages the connection between application and database  
	 */
	private class PromiseDatabase extends SQLiteOpenHelper{

		PromiseDatabase(Context context, String name, int version) {
			super(context, name, null, version);
		}

		/*
		@Override
		public void onConfigure(SQLiteDatabase db) {
			// Some model have old sqlite version which it doesnt support WAL mode.
			db.enableWriteAheadLogging();
			super.onConfigure(db);
		}
		 */

		@Override
		public void onCreate(SQLiteDatabase db) {
			try{				
				db.execSQL(createSchema);
			} catch(Exception e){
				e.printStackTrace();
			}       
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			try{
				db.execSQL(SQLQueries.dropTable + tableName);
				onCreate(db);
			} catch(Exception e){
				// to nothing
			}                
		}
	}

	DatabaseHandler(Context context, String createSchema, String tableName) {
		dbms = new PromiseDatabase(context, SQLQueries.dbName, SQLQueries.DB_VERSION);
		this.createSchema = createSchema;
		this.tableName = tableName;

		try{
			SQLiteDatabase db = dbms.getWritableDatabase();

			// below routine clean all tables regarding promise to prevent exception by unmatched schema
			// This is just for testing situation, we should use versioning after deploy app to the market.
			if(NEEDTOREMOVEALLTABLES){
				db.execSQL(SQLQueries.dropTable + tableName);
			}
			db.execSQL(createSchema);
			//db.enableWriteAheadLogging();
		} catch(Exception e){
			e.printStackTrace();
		}	
	}

	@Override
	protected void finalize() throws Throwable {
		SQLiteDatabase db = dbms.getWritableDatabase();
		db.close();
		super.finalize();
	}	

	public SparseArray<String> getColumns() {
		return columns;
	}


	public void setColumns(SparseArray<String> columns) {
		this.columns = columns;
	}

	public synchronized boolean addItem(IDatabaseRecord item){

		ContentValues v = importContentValues(item);
		SQLiteDatabase db = dbms.getWritableDatabase();

		try {
			db.insertWithOnConflict(tableName, null, v, SQLiteDatabase.CONFLICT_REPLACE);

		} catch(SQLException e){
			if(e.getMessage().contains("no such table")){
				db.execSQL(createSchema);
				db.insert(tableName, null, v);
				return true;
			}
			e.printStackTrace();
			return false;
		} catch(Exception e){
			e.printStackTrace();
			return false;
		} catch(Error e){
			e.printStackTrace();
			return false;
		}

		//showAll();
		return true;
	}

	public synchronized boolean addItems(Collection<? extends IDatabaseRecord> items){

		if(items.size() < 1){
			return false;
		}

		SQLiteDatabase db = dbms.getWritableDatabase();

		try {
			db.beginTransaction();
			for(IDatabaseRecord item : items){
				ContentValues v = importContentValues(item);
				db.insertWithOnConflict(tableName, null, v, SQLiteDatabase.CONFLICT_REPLACE);
			}
			db.setTransactionSuccessful();
		} catch(Exception e){             
			e.printStackTrace();
			return false;
		} finally {
			db.endTransaction();
		}

		//showAll();
		return true;
	}	

	public synchronized boolean updateItem(IDatabaseRecord item){

		ContentValues v = importContentValues(item);
		SQLiteDatabase db = dbms.getWritableDatabase();

		SparseArray<String> keys = item.getColumns();

		try {
			int res = 0;
			res = db.updateWithOnConflict(tableName, v, keys.get(0) + " = '" + item.getValue(0) + "'", null , SQLiteDatabase.CONFLICT_IGNORE);
			Log.d(LOG_TAG, "" + res + " items are updated!!! - updateItem");
		} catch(SQLException e){
			//e.printStackTrace();
			return false;
		} catch(Exception e){
			e.printStackTrace();
			return false;
		}

		//showAll();
		return true;
	}

	@SuppressWarnings("unused")
	private synchronized boolean updateItem(String where, ContentValues v){
		SQLiteDatabase db = dbms.getWritableDatabase();

		try {
			int res = 0;
			res = db.updateWithOnConflict(tableName, v, where, null , SQLiteDatabase.CONFLICT_IGNORE);
			Log.d(LOG_TAG, "" + res + " items are updated!!! - updateItem");
		} catch(SQLException e){
			//e.printStackTrace();
			return false;
		} catch(Exception e){
			e.printStackTrace();
			return false;
		}

		//showAll();
		return true;
	}
	

	public Collection<IDatabaseRecord> getDistinct(String sFieldName){
		Collection<IDatabaseRecord> items = new ArrayList<>();

		Cursor cursor = null;
		SQLiteDatabase db = dbms.getReadableDatabase();
		try {
			cursor = db.rawQuery(String.format(SQLQueries.selDistinct, sFieldName, tableName), null);    
		} catch(SQLException e){
			if(e.getMessage().contains("no such table")){
				db.execSQL(createSchema);
				return items;
			}
		}

		if(null == cursor){
			return items;
		} 

		if(cursor.moveToFirst()) {
			do {
				int end = cursor.getColumnCount();

				DatabaseRecordImpl record = new DatabaseRecordImpl(cursor.getString(0), columns);
				for(int cnt = 0; cnt < end; cnt++){
					String value = cursor.getString(cnt);
					record.setValue(cnt, value);
				}
				items.add(record);
			} while(cursor.moveToNext());
		}
		cursor.close();

		return items;        
	}

	Collection<IDatabaseRecord> getItems(){
		Collection<IDatabaseRecord> items = new ArrayList<>();

		Cursor cursor = null;
		SQLiteDatabase db = dbms.getReadableDatabase();
		try {
			cursor = db.rawQuery(SQLQueries.findAll + tableName, null);    
		} catch(SQLException e){
			if(e.getMessage().contains("no such table")){
				db.execSQL(createSchema);
				return items;
			}
		}

		if(null == cursor){
			return items;
		} 

		if(cursor.moveToFirst()) {
			do {
				int end = cursor.getColumnCount();

				DatabaseRecordImpl record = new DatabaseRecordImpl(cursor.getString(0), columns);
				for(int cnt = 0; cnt < end; cnt++){
					String value = cursor.getString(cnt);
					record.setValue(cnt, value);
				}
				items.add(record);
			} while(cursor.moveToNext());
		}
		cursor.close();

		return items;        
	}

	Collection<IDatabaseRecord> getItems(String pkFieldName, String value){
		Collection<IDatabaseRecord> items = new ArrayList<>();

		Cursor cursor = null;
		SQLiteDatabase db = dbms.getReadableDatabase();
		try {
			cursor = db.rawQuery(String.format(SQLQueries.findSome, tableName, pkFieldName), new String[] {value});    
		} catch(SQLException e){
			if(e.getMessage().contains("no such table")){
				db.execSQL(createSchema);
				return items;
			}
		}     

		if(null == cursor){
			return items;
		}

		if(cursor.moveToFirst()) {
			do {
				int end = cursor.getColumnCount();

				DatabaseRecordImpl record = new DatabaseRecordImpl(cursor.getString(0), columns);
				for(int cnt = 0; cnt < end; cnt++){
					String v = cursor.getString(cnt);
					record.setValue(cnt, v);
				}
				items.add(record);
			} while(cursor.moveToNext());
		}
		cursor.close();

		return items;        
	}
	
	public Collection<IDatabaseRecord> getItems(String pkFieldName, long value){
		return getItems(pkFieldName, ((Long)value).toString());
	}
		
	public Collection<IDatabaseRecord> getItems(String sWhere){
		Collection<IDatabaseRecord> items = new ArrayList<>();

		Cursor cursor = null;
		SQLiteDatabase db = dbms.getReadableDatabase();
		try {
			cursor = db.rawQuery(String.format(SQLQueries.findSomeWithWhere, tableName, sWhere), null);    
		} catch(SQLException e){
			if(e.getMessage().contains("no such table")){
				db.execSQL(createSchema);
				return items;
			}
		}     

		if(null == cursor){
			return items;
		}

		if(cursor.moveToFirst()) {
			do {
				int end = cursor.getColumnCount();

				DatabaseRecordImpl record = new DatabaseRecordImpl(cursor.getString(0), columns);
				for(int cnt = 0; cnt < end; cnt++){
					String v = cursor.getString(cnt);
					record.setValue(cnt, v);
				}
				items.add(record);
			} while(cursor.moveToNext());
		}
		cursor.close();

		return items;        
	}

	public IDatabaseRecord getItem(String pkFieldName, String value){

		Cursor cursor = null;
		SQLiteDatabase db = dbms.getReadableDatabase();
		try {
			cursor = db.rawQuery(String.format(SQLQueries.findSome, tableName, pkFieldName), new String[] {value});    
		} catch(SQLException e){
			if(e.getMessage().contains("no such table")){
				db.execSQL(createSchema);
				return null;
			}
		}

		if(null == cursor){
			return null;
		}

		DatabaseRecordImpl record = null;

		if(cursor.moveToFirst()) {
			int end = cursor.getColumnCount();

			record = new DatabaseRecordImpl(cursor.getString(0), columns);
			for(int cnt = 0; cnt < end; cnt++){
				String v = cursor.getString(cnt);
				record.setValue(cnt, v);
			}        
		}
		cursor.close();
		return record;
	}

	int getCountAll() {
		String countQuery = SQLQueries.countAll + tableName;
		SQLiteDatabase db = dbms.getReadableDatabase();
		Cursor cursor = db.rawQuery(countQuery, null);
		int count = cursor.getCount();
		cursor.close();
		return count;
	}

	public int getCount(String where) {
		String countQuery = String.format(SQLQueries.countSomeWithWhereStatement, tableName, where);
		SQLiteDatabase db = dbms.getReadableDatabase();
		Cursor cursor = db.rawQuery(countQuery, null);
		int count = cursor.getCount();
		cursor.close();
		return count;
	}

	int getCount(String pkFieldName, String value) {
		String countQuery = String.format(SQLQueries.countSome, tableName, pkFieldName);
		SQLiteDatabase db = dbms.getReadableDatabase();
		Cursor cursor = db.rawQuery(countQuery, new String[] {value} );
		int count = cursor.getCount();
		cursor.close();
		return count;
	}
	
	public long getValue(String query, String columnName) {
		SQLiteDatabase db = dbms.getReadableDatabase();
		Cursor cursor = db.rawQuery(query, null);
		long result = -1L;
		if(cursor.moveToFirst()){
			result = cursor.getLong(cursor.getColumnIndex(columnName));
		}		
		cursor.close();
		return result;
	}

	synchronized void deleteAll(){
		SQLiteDatabase db = dbms.getWritableDatabase();

		try{
			db.delete(tableName, null, null);
		} catch(Exception e){
			// to nothing
		}
	}

	public synchronized int delete(String pkFieldName, String value){
		String countQuery = String.format(SQLQueries.deleteSome, tableName, pkFieldName);
		SQLiteDatabase db = dbms.getWritableDatabase();
		Cursor cursor;
		int count = 0;
		
		try{
			cursor = db.rawQuery(countQuery, new String[] {value} );
			count = cursor.getCount();
			cursor.close();
		} catch(SQLException e){
			if(e.getMessage().contains("no such table")){
				db.execSQL(createSchema);
				return 0;
			}
		}
		return count;
	}

	public synchronized int delete(String where){
		String countQuery = String.format(SQLQueries.deleteSomeWithWhereStatement, tableName, where);
		SQLiteDatabase db = dbms.getWritableDatabase();
		Cursor cursor;
		int count = 0;
		
		try{
			cursor = db.rawQuery(countQuery, null );
			count = cursor.getCount();
			cursor.close();
		} catch(SQLException e){
			if(e.getMessage().contains("no such table")){
				db.execSQL(createSchema);
				return 0;
			}else{
				showAll();
			}
		}
		
		return count;
	}

	private ContentValues importContentValues(IDatabaseRecord item) {
		ContentValues v = new ContentValues();
		SparseArray<String> keys = item.getColumns();

		int key;
		for(int cnt = 0; cnt < keys.size() ; cnt++){
			key = keys.keyAt(cnt);
			// Because of finding problem, below util inserting ' + value + '
			//String columnValue = DatabaseUtils.sqlEscapeString(item.getValue(key));
			String columnValue = item.getValue(key);
			if(null != columnValue){
				v.put(keys.valueAt(cnt), columnValue);
			}
		}
		return v;
	}

	/**
	 * for Debugging
	 */

	void showAll(){

		if(!DEBUGALLDATAS)
		{
			return;
		}

		String columnName = " ";
		String columnValue = "";
		boolean first = true;

		Log.d(LOG_TAG, ">> ALL DATAS");
		Log.d(LOG_TAG, "----------------------------------------------------------------------------------------");

		SQLiteDatabase db = dbms.getReadableDatabase();
		try (Cursor cursor = db.rawQuery(SQLQueries.findAll + tableName, null)) {
			if (cursor.moveToFirst()) {
				do {
					int end = cursor.getColumnCount();

					for (int cnt = 0; cnt < end; cnt++) {

						if (first) {
							columnName += cursor.getColumnName(cnt) + "\t\t";
						}
						columnValue += cursor.getString(cnt) + "\t";
					}

					if (first) {
						Log.d(LOG_TAG, columnName);
						Log.d(LOG_TAG, "----------------------------------------------------------------------------------------");
						first = false;
					}

					Log.d(LOG_TAG, columnValue);
					columnValue = "";

				} while (cursor.moveToNext());
			}
		} catch (Exception e) {
			// do nothing
		}

	}

}
