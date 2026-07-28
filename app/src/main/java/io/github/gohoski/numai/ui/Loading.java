package io.github.gohoski.numai.ui;

import android.app.ProgressDialog;
import android.content.Context;

import io.github.gohoski.numai.R;

public class Loading extends ProgressDialog {
    private Loading(Context context, int message) {
        super(context);
        this.setMessage(context.getString(message));
        this.setCancelable(false);
        this.show();
    }

    public Loading(Context context) {
        this(context, R.string.loading);
    }
}
