package me.blog.imhallower.wimple;

import android.os.Message;

public interface IWimpleFragment {
	
	abstract public void handleMessage(Message msg);
	
	abstract public void refreshView();
	
	abstract public void setActivityInstance(WimpleActivity instance);
}
