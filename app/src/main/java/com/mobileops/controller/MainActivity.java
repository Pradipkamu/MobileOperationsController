package com.mobileops.controller;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView makeCard(LinearLayout parent, String heading,
                              String description) {
        TextView card = new TextView(this);
        card.setText(heading + "\n\n" + description);
        card.setTextSize(16);
        card.setTextColor(Color.rgb(30, 41, 59));
        card.setPadding(24, 22, 24, 22);
        card.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        parent.addView(card, params);
        return card;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 30, 20, 20);
        root.setBackgroundColor(Color.rgb(241, 245, 249));

        TextView title = new TextView(this);
        title.setText("MOBILE OPERATIONS");
        title.setTextSize(23);
        title.setTextColor(Color.rgb(15, 23, 42));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(4, 12, 4, 24);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Operations Controller  |  v0.1");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle);

        makeCard(root, "PRADIPWEB CONNECTION",
                 "Not connected yet");

        makeCard(root, "APPLICATION HEALTH",
                 "Backend  |  Pending connection\nFrontend  |  Pending connection\nDatabase  |  Pending connection");

        makeCard(root, "DOCKER SERVICES",
                 "Container monitoring will be added next");

        makeCard(root, "APPLICATION LOGS",
                 "Backend  |  Frontend  |  Database");

        makeCard(root, "OPERATIONS",
                 "Read-only monitoring mode");

        setContentView(root);
    }
}
