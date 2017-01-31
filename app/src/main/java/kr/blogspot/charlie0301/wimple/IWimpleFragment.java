package kr.blogspot.charlie0301.wimple;

import android.os.Message;

public interface IWimpleFragment {
	
	void handleMessage(Message msg);
	
	void refreshView();
	
	void setActivityInstance(WimpleActivity instance);
}
