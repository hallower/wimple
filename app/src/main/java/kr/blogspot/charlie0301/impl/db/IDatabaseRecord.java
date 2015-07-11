package kr.blogspot.charlie0301.impl.db;

import android.util.SparseArray;


public interface IDatabaseRecord {

    abstract public String getKeyValue();

    abstract public SparseArray<String> getColumns();

    abstract public boolean setValues(SparseArray<String> values);
    
    abstract public String getValue(int columnID);
    
    abstract public SparseArray<String> getValues();
}
