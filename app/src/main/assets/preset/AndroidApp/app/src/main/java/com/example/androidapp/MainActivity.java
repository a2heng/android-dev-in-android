package com.example.androidapp;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView view = new TextView(this);
        view.setText("Hello from Adia");
        view.setTextSize(24);
        view.setGravity(Gravity.CENTER);
        setContentView(view);
    }
}
