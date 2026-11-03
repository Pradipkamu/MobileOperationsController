package com.mobileops.controller;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private final int ink = Color.rgb(15, 23, 42);
    private final int muted = Color.rgb(100, 116, 139);
    private final int canvas = Color.rgb(244, 247, 251);
    private final int blue = Color.rgb(37, 99, 235);
    private LinearLayout root;

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, height));
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout panel = vertical();
        panel.setPadding(18, 16, 18, 16);
        panel.setBackground(shape(Color.WHITE, 18));
        panel.setElevation(2);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 14);
        parent.addView(panel, params);
        return panel;
    }

    private void sectionTitle(LinearLayout parent, String title, String subtitle) {
        TextView heading = text(title, 17, ink, true);
        parent.addView(heading);
        if (subtitle != null) {
            addSpace(parent, 4);
            parent.addView(text(subtitle, 12, muted, false));
        }
        addSpace(parent, 12);
    }

    private void statusRow(LinearLayout parent, String name, String detail,
                           String status) {
        LinearLayout row = horizontal();
        LinearLayout left = vertical();
        left.addView(text(name, 14, ink, true));
        addSpace(left, 3);
        left.addView(text(detail, 12, muted, false));
        row.addView(left, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView badge = text(status, 11, Color.rgb(146, 64, 14), true);
        badge.setPadding(10, 6, 10, 6);
        badge.setBackground(shape(Color.rgb(255, 247, 237), 30));
        row.addView(badge);
        parent.addView(row);
        addSpace(parent, 13);
    }

    private void labelValue(LinearLayout parent, String label, String value) {
        LinearLayout row = horizontal();
        row.addView(text(label, 13, muted, false),
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text(value, 13, ink, true));
        parent.addView(row);
        addSpace(parent, 10);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(canvas);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(canvas);

        root = vertical();
        root.setPadding(20, 22, 20, 24);
        scroll.addView(root);

        LinearLayout header = horizontal();
        LinearLayout heading = vertical();
        heading.addView(text("MOBILE OPERATIONS", 20, ink, true));
        addSpace(heading, 4);
        heading.addView(text("Operations Controller  •  v0.2", 12, muted, false));
        header.addView(heading, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView mode = text("READ ONLY", 10, Color.rgb(21, 128, 61), true);
        mode.setPadding(10, 7, 10, 7);
        mode.setBackground(shape(Color.rgb(220, 252, 231), 30));
        header.addView(mode);
        root.addView(header);
        addSpace(root, 20);

        LinearLayout connection = card(root);
        TextView eyebrow = text("SERVER CONNECTION", 11, muted, true);
        connection.addView(eyebrow);
        addSpace(connection, 10);
        connection.addView(text("Pradipweb", 21, ink, true));
        addSpace(connection, 5);
        connection.addView(text("137.23.40.188  •  SSH", 13, muted, false));
        addSpace(connection, 14);
        LinearLayout state = horizontal();
        View dot = new View(this);
        dot.setBackground(shape(Color.rgb(217, 119, 6), 20));
        state.addView(dot, new LinearLayout.LayoutParams(9, 9));
        TextView stateText = text("Not connected", 13,
            Color.rgb(146, 64, 14), true);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        stateParams.setMargins(8, 0, 0, 0);
        state.addView(stateText, stateParams);
        connection.addView(state);
        addSpace(connection, 8);
        connection.addView(text("Secure SSH setup is not configured in this app yet.",
            12, muted, false));

        LinearLayout health = card(root);
        sectionTitle(health, "APPLICATION HEALTH",
            "Live checks will appear after secure connection is enabled");
        statusRow(health, "Backend API", "ProductionReviewWebApp • port 8000", "NOT CHECKED");
        statusRow(health, "Frontend", "ProductionReviewWebApp • port 5173", "NOT CHECKED");
        statusRow(health, "PostgreSQL", "Production database • port 5432", "NOT CHECKED");

        LinearLayout services = card(root);
        sectionTitle(services, "DOCKER SERVICES",
            "Expected containers on Pradipweb");
        statusRow(services, "Frontend container",
            "productionreviewwebapp-frontend-1", "UNKNOWN");
        statusRow(services, "Backend container",
            "productionreviewwebapp-backend-1", "UNKNOWN");
        statusRow(services, "Database container",
            "productionreviewwebapp-db-1", "UNKNOWN");

        LinearLayout logs = card(root);
        sectionTitle(logs, "RECENT APPLICATION LOGS",
            "Log output is unavailable until the SSH connection is configured");
        TextView empty = text("No logs retrieved", 14, muted, false);
        empty.setPadding(0, 4, 0, 8);
        logs.addView(empty);

        LinearLayout target = card(root);
        sectionTitle(target, "SERVER DETAILS", "Saved target information");
        labelValue(target, "SSH host", "137.23.40.188");
        labelValue(target, "SSH user", "ubuntu");
        labelValue(target, "Access mode", "Read-only");
        labelValue(target, "Credentials", "Not imported");

        TextView note = text(
            "NEXT: Add encrypted SSH key storage, host-key verification and read-only health commands. No server changes are performed by this app.",
            12, muted, false);
        note.setPadding(4, 4, 4, 8);
        root.addView(note);

        setContentView(scroll);
    }
}
