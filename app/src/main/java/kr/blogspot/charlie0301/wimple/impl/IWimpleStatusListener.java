package kr.blogspot.charlie0301.wimple.impl;


public interface IWimpleStatusListener {
	
	public void onLoggedIn(boolean status);
	
	public void onLoggedOut();
	
	public void onProfilePictureUpdated();
	
	public void onNetworkConnectionEstablished();
	
	public void onNetworkConnectionLost();
	
}
