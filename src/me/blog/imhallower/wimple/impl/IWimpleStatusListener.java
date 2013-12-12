package me.blog.imhallower.wimple.impl;


public interface IWimpleStatusListener {
	
	public void onLoggedIn(boolean status);
	
	public void onLoggedOut();
	
	public void onNetworkConnectionEstablished();
	
	public void onNetworkConnectionLost();
	
}
