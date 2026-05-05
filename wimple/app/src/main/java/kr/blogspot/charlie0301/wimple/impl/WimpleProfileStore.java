package kr.blogspot.charlie0301.wimple.impl;

import android.content.Context;

import java.io.File;

final class WimpleProfileStore {

    private final Context context;

    WimpleProfileStore(Context context) {
        this.context = context;
    }

    String getProfilePath(String userID) {
        String filename = userID == null ? "" : userID.replace("@", "_");
        return new File(context.getFilesDir(), filename + ".bin").getAbsolutePath();
    }
}
