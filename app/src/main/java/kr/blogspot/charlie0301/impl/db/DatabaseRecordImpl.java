package kr.blogspot.charlie0301.impl.db;

import android.util.SparseArray;

public class DatabaseRecordImpl implements IDatabaseRecord {

    private final String primaryKeyValue;
    private final SparseArray<String> columns;
    private SparseArray<String> values;    

    public DatabaseRecordImpl(String primaryKeyValue, SparseArray<String> columns) {
        super();
        this.primaryKeyValue = primaryKeyValue;
        this.columns = columns;
        values = new SparseArray<String>();
    }

    public DatabaseRecordImpl setValue(int key, String value) {
        if(null == value){
            value = "";
        }
        this.values.put(key, value);
        return this;
    }

    @Override
    public String getKeyValue() { 
        return primaryKeyValue;
    }

    @Override
    public SparseArray<String> getColumns() {
        return columns;
    }


    @Override
    public boolean setValues(SparseArray<String> values) {
        this.values = values;
        return true;
    }


    @Override
    public String getValue(int columnID) {

        String value = values.get(columnID);
        if(null == value){
            return "";
        }
        return value;
    }

    @Override
    public final SparseArray<String> getValues() {
        return values;
    }

}
