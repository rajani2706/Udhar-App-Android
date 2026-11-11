
package com.smart_nootes.udhaarapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.database.Cursor;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private TextView txtWelcome, txtShopName;
    private TextView txtTotalBalance, txtTodayCredit;
    private TextView txtDashboardCustomerCount;

    private EditText edtDashboardSearch;
    private LinearLayout layoutDashboardCustomers;

    private Button btnCustomers, btnLogout;

    private DatabaseHelper databaseHelper;
    private CloudSyncManager cloudSyncManager;

    private int userId = -1;

    private final ArrayList<CustomerRow> allCustomers =
            new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        txtWelcome = findViewById(R.id.txtWelcome);
        txtShopName = findViewById(R.id.txtShopName);
        txtTotalBalance = findViewById(R.id.txtTotalBalance);
        txtTodayCredit = findViewById(R.id.txtTodayCredit);

        txtDashboardCustomerCount =
                findViewById(R.id.txtDashboardCustomerCount);

        edtDashboardSearch =
                findViewById(R.id.edtDashboardSearch);

        layoutDashboardCustomers =
                findViewById(R.id.layoutDashboardCustomers);

        btnCustomers = findViewById(R.id.btnCustomers);
        btnLogout = findViewById(R.id.btnLogout);

        databaseHelper = new DatabaseHelper(this);

        // Initialize Cloud Sync Manager
        cloudSyncManager = new CloudSyncManager(databaseHelper);

        SharedPreferences preferences = getSharedPreferences(
                "SmartKhataSession",
                MODE_PRIVATE
        );

        String mobile = preferences.getString("mobile", "");

        String ownerName =
                getIntent().getStringExtra("owner_name");

        String shopName =
                getIntent().getStringExtra("shop_name");

        if (mobile != null && !mobile.trim().isEmpty()) {

            userId = databaseHelper.getUserId(mobile);

        } else if (ownerName != null && shopName != null) {

            userId = databaseHelper.getUserIdByOwnerAndShop(
                    ownerName,
                    shopName
            );
        }

        if (ownerName == null || ownerName.trim().isEmpty()) {

            ownerName = (mobile == null || mobile.trim().isEmpty())
                    ? ""
                    : databaseHelper.getOwnerName(mobile);
        }

        if (shopName == null || shopName.trim().isEmpty()) {

            shopName = (mobile == null || mobile.trim().isEmpty())
                    ? ""
                    : databaseHelper.getShopName(mobile);
        }

        txtWelcome.setText(
                "Welcome" +
                        ((ownerName == null || ownerName.trim().isEmpty())
                                ? " 👋"
                                : ", " + ownerName + " 👋")
        );

        txtShopName.setText(
                shopName == null || shopName.trim().isEmpty()
                        ? "Your Shop"
                        : shopName
        );

        if (userId < 0) {

            Toast.makeText(
                    this,
                    "User account not found. Please login again.",
                    Toast.LENGTH_LONG
            ).show();
        }

        // Search customers by name or mobile number
        edtDashboardSearch.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                renderCustomers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Add Customer button
        btnCustomers.setOnClickListener(v -> {

            startActivity(new Intent(
                    DashboardActivity.this,
                    AddCustomerActivity.class
            ));
        });

        // Logout
        btnLogout.setOnClickListener(v -> {

            FirebaseAuth.getInstance().signOut();

            getSharedPreferences(
                    "SmartKhataSession",
                    MODE_PRIVATE
            ).edit().clear().apply();

            Intent intent = new Intent(
                    DashboardActivity.this,
                    MainActivity.class
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);
            finish();
        });
    }

    // Start realtime sync whenever Dashboard becomes active
    @Override
    protected void onStart() {
        super.onStart();

        if (cloudSyncManager != null && userId >= 0) {

            cloudSyncManager.startRealtimeSync(() -> {

                runOnUiThread(() -> {

                    if (!isFinishing() && !isDestroyed()) {

                        refreshDashboard();
                    }
                });
            });
        }
    }

    // Stop realtime sync when Dashboard is no longer active
    @Override
    protected void onStop() {
        super.onStop();

        if (cloudSyncManager != null) {
            cloudSyncManager.stopRealtimeSync();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        refreshDashboard();
    }

    // Refresh dashboard information
    private void refreshDashboard() {

        if (databaseHelper == null || userId < 0) {
            return;
        }

        double totalBalance =
                databaseHelper.getUserTotalBalance(userId);

        double todayUdhar =
                databaseHelper.getTodayUdhar(userId);

        // Total Lena / Jama
        if (totalBalance < 0) {

            txtTotalBalance.setText(
                    "Jama: " + formatRupees(Math.abs(totalBalance))
            );

            txtTotalBalance.setTextColor(
                    Color.rgb(33, 90, 180)
            );

        } else {

            txtTotalBalance.setText(
                    "Lena: " + formatRupees(totalBalance)
            );

            txtTotalBalance.setTextColor(
                    Color.rgb(24, 118, 59)
            );
        }

        // Today's Udhar / Jama
        if (todayUdhar < 0) {

            txtTodayCredit.setText(
                    "Jama: " + formatRupees(Math.abs(todayUdhar))
            );

        } else {

            txtTodayCredit.setText(
                    "Udhar: " + formatRupees(todayUdhar)
            );
        }

        loadCustomers();
    }

    // Load customers from local database
    private void loadCustomers() {

        allCustomers.clear();

        Cursor cursor = databaseHelper.getCustomers(userId);

        if (cursor != null) {

            try {

                while (cursor.moveToNext()) {

                    int id = cursor.getInt(
                            cursor.getColumnIndexOrThrow("id")
                    );

                    String name = cursor.getString(
                            cursor.getColumnIndexOrThrow("name")
                    );

                    String mobile = cursor.getString(
                            cursor.getColumnIndexOrThrow("mobile")
                    );

                    String address = cursor.getString(
                            cursor.getColumnIndexOrThrow("address")
                    );

                    double balance =
                            databaseHelper.getCustomerBalance(id);

                    allCustomers.add(new CustomerRow(
                            id,
                            name,
                            mobile,
                            address,
                            balance
                    ));
                }

            } finally {
                cursor.close();
            }
        }

        renderCustomers(edtDashboardSearch.getText().toString());
    }

    // Search and display customers
    private void renderCustomers(String query) {

        layoutDashboardCustomers.removeAllViews();

        String search = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);

        int shown = 0;

        for (CustomerRow customer : allCustomers) {

            String name = customer.name == null
                    ? ""
                    : customer.name.toLowerCase(Locale.ROOT);

            String mobile = customer.mobile == null
                    ? ""
                    : customer.mobile.toLowerCase(Locale.ROOT);

            if (!name.contains(search) &&
                    !mobile.contains(search)) {
                continue;
            }

            shown++;

            layoutDashboardCustomers.addView(
                    createCustomerCard(customer)
            );
        }

        txtDashboardCustomerCount.setText(
                shown + (shown == 1 ? " Customer" : " Customers")
        );

        if (shown == 0) {

            TextView empty = new TextView(this);

            empty.setText(search.isEmpty()
                    ? "Abhi koi customer nahi hai. + Add Customer par tap karein."
                    : "Koi matching customer nahi mila.");

            empty.setTextColor(Color.GRAY);
            empty.setTextSize(14);
            empty.setPadding(12, 22, 12, 22);

            layoutDashboardCustomers.addView(empty);
        }
    }

    // Create individual customer card
    private View createCustomerCard(CustomerRow customer) {

        LinearLayout card = new LinearLayout(this);

        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(14, 15, 12, 15);

        GradientDrawable background = new GradientDrawable();

        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(14));
        background.setStroke(
                dp(1),
                Color.rgb(230, 234, 239)
        );

        card.setBackground(background);
        card.setElevation(dp(1));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, dp(5), 0, dp(5));

        card.setLayoutParams(cardParams);

        // Customer avatar
        TextView avatar = new TextView(this);

        avatar.setText(
                customer.name == null ||
                        customer.name.trim().isEmpty()
                        ? "?"
                        : customer.name.substring(0, 1).toUpperCase()
        );

        avatar.setTextColor(Color.rgb(24, 118, 59));
        avatar.setTextSize(19);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);

        GradientDrawable avatarBg = new GradientDrawable();

        avatarBg.setColor(Color.rgb(226, 244, 232));
        avatarBg.setShape(GradientDrawable.OVAL);

        avatar.setBackground(avatarBg);

        LinearLayout.LayoutParams avatarParams =
                new LinearLayout.LayoutParams(dp(46), dp(46));

        avatarParams.setMarginEnd(dp(12));

        card.addView(avatar, avatarParams);

        // Customer details
        LinearLayout details = new LinearLayout(this);

        details.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        TextView name = new TextView(this);

        name.setText(customer.name == null ? "" : customer.name);
        name.setTextColor(Color.rgb(34, 34, 34));
        name.setTextSize(16);
        name.setTypeface(null, Typeface.BOLD);

        details.addView(name);

        TextView mobile = new TextView(this);

        mobile.setText(
                customer.mobile == null ||
                        customer.mobile.trim().isEmpty()
                        ? "Mobile number not added"
                        : customer.mobile
        );

        mobile.setTextColor(Color.GRAY);
        mobile.setTextSize(13);

        LinearLayout.LayoutParams mobileParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        mobileParams.topMargin = dp(3);

        details.addView(mobile, mobileParams);

        TextView balance = new TextView(this);

        String balanceLabel;

        if (customer.balance > 0) {

            balanceLabel =
                    "Lena hai: " + formatRupees(customer.balance);

            balance.setTextColor(Color.rgb(24, 118, 59));

        } else if (customer.balance < 0) {

            balanceLabel =
                    "Jama hai: " +
                            formatRupees(Math.abs(customer.balance));

            balance.setTextColor(Color.rgb(33, 90, 180));

        } else {

            balanceLabel =
                    "Balance: " + formatRupees(0);

            balance.setTextColor(Color.GRAY);
        }

        balance.setText(balanceLabel);
        balance.setTextSize(14);
        balance.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams balanceParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        balanceParams.topMargin = dp(5);

        details.addView(balance, balanceParams);

        card.addView(details, detailsParams);

        // Arrow
        TextView arrow = new TextView(this);

        arrow.setText("›");
        arrow.setTextColor(Color.GRAY);
        arrow.setTextSize(30);
        arrow.setGravity(Gravity.CENTER);

        card.addView(arrow, new LinearLayout.LayoutParams(
                dp(25),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Open customer details when tapped
        card.setOnClickListener(v -> {

            Intent intent = new Intent(
                    DashboardActivity.this,
                    CustomerDetailsActivity.class
            );

            intent.putExtra("customer_id", customer.id);
            intent.putExtra("customer_name", customer.name);
            intent.putExtra("customer_mobile", customer.mobile);
            intent.putExtra("customer_address", customer.address);

            startActivity(intent);
        });

        return card;
    }

    private int dp(int value) {

        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private String formatRupees(double amount) {

        return String.format(
                Locale.getDefault(),
                "₹ %.2f",
                amount
        );
    }

    private static class CustomerRow {

        final int id;
        final String name;
        final String mobile;
        final String address;
        final double balance;

        CustomerRow(
                int id,
                String name,
                String mobile,
                String address,
                double balance
        ) {

            this.id = id;
            this.name = name;
            this.mobile = mobile;
            this.address = address;
            this.balance = balance;
        }
    }
}
