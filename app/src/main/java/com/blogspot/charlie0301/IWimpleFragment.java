package com.blogspot.charlie0301;

import android.os.Message;

public interface IWimpleFragment {
	
	abstract public void handleMessage(Message msg);
	
	abstract public void refreshView();
	
	abstract public void setActivityInstance(WimpleActivity instance);
}
