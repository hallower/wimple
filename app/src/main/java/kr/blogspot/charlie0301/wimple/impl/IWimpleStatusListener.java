package kr.blogspot.charlie0301.wimple.impl;


public interface IWimpleStatusListener {

    void onLoggedIn(boolean status);

    void onLoggedOut();

    void onProfilePictureUpdated();

    void onNetworkConnectionEstablished();

    void onNetworkConnectionLost();

}
