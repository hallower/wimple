package kr.blogspot.charlie0301.wimple.impl.db;

import android.util.SparseArray;


public interface IDatabaseRecord {

    String getKeyValue();

    SparseArray<String> getColumns();

    boolean setValues(SparseArray<String> values);

    String getValue(int columnID);

    SparseArray<String> getValues();
}
