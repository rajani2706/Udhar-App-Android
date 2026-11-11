
package com.smart_nootes.udhaarapp;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class CustomerDetailsActivity extends AppCompatActivity {

    private TextView txtCustomerName;
    private TextView txtCustomerMobile;
    private TextView txtCustomerAddress;
    private TextView txtCustomerBalance;
    private TextView txtTransactionCount;

    private Button btnEditCustomer;
    private Button btnDeleteCustomer;
    private Button btnCustomerAddUdhar;
    private Button btnCustomerPayment;

    private LinearLayout layoutCustomerTransactions;

    private DatabaseHelper databaseHelper;
    private CloudSyncManager cloudSyncManager;

    private int customerId = -1;
    private String customerName = "";
    private String customerMobile = "";
    private String customerAddress = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_details);

        txtCustomerName = findViewById(R.id.txtCustomerName);
        txtCustomerMobile = findViewById(R.id.txtCustomerMobile);
        txtCustomerAddress = findViewById(R.id.txtCustomerAddress);
        txtCustomerBalance = findViewById(R.id.txtCustomerBalance);
        txtTransactionCount = findViewById(R.id.txtTransactionCount);

        layoutCustomerTransactions =
                findViewById(R.id.layoutCustomerTransactions);

        btnEditCustomer = findViewById(R.id.btnEditCustomer);
        btnDeleteCustomer = findViewById(R.id.btnDeleteCustomer);
        btnCustomerAddUdhar = findViewById(R.id.btnCustomerAddUdhar);
        btnCustomerPayment = findViewById(R.id.btnCustomerPayment);

        databaseHelper = new DatabaseHelper(this);
        cloudSyncManager = new CloudSyncManager(databaseHelper);

        customerId = getIntent().getIntExtra("customer_id", -1);

        if (customerId == -1) {
            Toast.makeText(
                    this,
                    "Customer nahi mila",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
            return;
        }

        btnEditCustomer.setOnClickListener(v -> openEditCustomer());
        btnDeleteCustomer.setOnClickListener(v -> confirmDeleteCustomer());

        btnCustomerAddUdhar.setOnClickListener(v ->
                openTransactionScreen("udhar"));

        btnCustomerPayment.setOnClickListener(v ->
                openTransactionScreen("payment"));

        loadCustomerDetails();
        loadRecentTransactions();
    }

    private void openTransactionScreen(String mode) {
        Intent intent;

        if ("udhar".equals(mode)) {
            intent = new Intent(this, AddUdharActivity.class);
        } else {
            intent = new Intent(this, PaymentActivity.class);
        }

        // Pass selected customer information to transaction screen.
        intent.putExtra("customer_id", customerId);
        intent.putExtra("customer_name", customerName);
        intent.putExtra("customer_mobile", customerMobile);
        intent.putExtra("customer_address", customerAddress);

        startActivity(intent);
    }

    private void loadCustomerDetails() {
        Cursor cursor = databaseHelper.getCustomer(customerId);

        if (cursor == null) {
            Toast.makeText(
                    this,
                    "Customer nahi mila",
                    Toast.LENGTH_SHORT
            ).show();
            finish();
            return;
        }

        boolean found = false;

        try {
            if (cursor.moveToFirst()) {
                found = true;

                String name = cursor.getString(
                        cursor.getColumnIndexOrThrow("name")
                );

                String mobile = cursor.getString(
                        cursor.getColumnIndexOrThrow("mobile")
                );

                String address = cursor.getString(
                        cursor.getColumnIndexOrThrow("address")
                );

                customerName = name == null ? "" : name;
                customerMobile = mobile == null ? "" : mobile;
                customerAddress = address == null ? "" : address;

                txtCustomerName.setText(
                        customerName.isEmpty()
                                ? "Customer Name"
                                : customerName
                );

                txtCustomerMobile.setText(
                        customerMobile.trim().isEmpty()
                                ? "Mobile number not added"
                                : "Mobile: " + customerMobile
                );

                txtCustomerAddress.setText(
                        customerAddress.trim().isEmpty()
                                ? "Address not added"
                                : "Address: " + customerAddress
                );

                double balance =
                        databaseHelper.getCustomerBalance(customerId);

                if (balance > 0) {
                    txtCustomerBalance.setText(
                            "Lena hai: " + formatRupees(balance)
                    );

                    txtCustomerBalance.setTextColor(
                            Color.rgb(24, 118, 59)
                    );

                } else if (balance < 0) {
                    txtCustomerBalance.setText(
                            "Jama hai: " +
                                    formatRupees(Math.abs(balance))
                    );

                    txtCustomerBalance.setTextColor(
                            Color.rgb(33, 90, 180)
                    );

                } else {
                    txtCustomerBalance.setText(
                            "Balance: " + formatRupees(0)
                    );

                    txtCustomerBalance.setTextColor(Color.DKGRAY);
                }
            }
        } finally {
            cursor.close();
        }

        if (!found) {
            Toast.makeText(
                    this,
                    "Customer nahi mila",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
        }
    }

    private void loadRecentTransactions() {
        layoutCustomerTransactions.removeAllViews();

        Cursor cursor = databaseHelper.getTransactions(customerId);
        int count = 0;

        if (cursor != null) {
            try {
                while (cursor.moveToNext() && count < 10) {
                    count++;

                    String type = cursor.getString(
                            cursor.getColumnIndexOrThrow("type")
                    );

                    double amount = cursor.getDouble(
                            cursor.getColumnIndexOrThrow("amount")
                    );

                    String description = cursor.getString(
                            cursor.getColumnIndexOrThrow("description")
                    );

                    String date = cursor.getString(
                            cursor.getColumnIndexOrThrow("date")
                    );

                    layoutCustomerTransactions.addView(
                            createTransactionRow(
                                    type,
                                    amount,
                                    description,
                                    date
                            )
                    );
                }
            } finally {
                cursor.close();
            }
        }

        txtTransactionCount.setText(
                count == 0
                        ? "No transactions yet"
                        : "Showing " + count + " recent transactions"
        );

        if (count == 0) {
            TextView empty = new TextView(this);

            empty.setText(
                    "Is customer ke liye abhi koi transaction nahi hai."
            );

            empty.setTextColor(Color.GRAY);
            empty.setTextSize(14);
            empty.setPadding(12, 20, 12, 20);

            layoutCustomerTransactions.addView(empty);
        }
    }

    private LinearLayout createTransactionRow(
            String type,
            double amount,
            String description,
            String date
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(13), dp(12), dp(13));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.rgb(230, 234, 239));

        row.setBackground(bg);

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        rowParams.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(rowParams);

        boolean isUdhar = "UDHAR".equalsIgnoreCase(type);

        TextView symbol = new TextView(this);
        symbol.setText(isUdhar ? "↑" : "↓");
        symbol.setTextSize(23);
        symbol.setGravity(Gravity.CENTER);

        symbol.setTextColor(
                isUdhar
                        ? Color.rgb(24, 118, 59)
                        : Color.rgb(33, 90, 180)
        );

        row.addView(symbol, new LinearLayout.LayoutParams(
                dp(35), dp(40)
        ));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        infoParams.setMargins(dp(8), 0, dp(8), 0);

        TextView title = new TextView(this);
        title.setText(isUdhar ? "Udhar diya" : "Payment mila");
        title.setTextColor(Color.rgb(34, 34, 34));
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);

        info.addView(title);

        if (description != null && !description.trim().isEmpty()) {
            TextView note = new TextView(this);
            note.setText(description);
            note.setTextColor(Color.DKGRAY);
            note.setTextSize(13);

            info.addView(note);
        }

        TextView dateText = new TextView(this);
        dateText.setText(date == null ? "" : date);
        dateText.setTextColor(Color.GRAY);
        dateText.setTextSize(11);

        info.addView(dateText);
        row.addView(info, infoParams);

        TextView amountText = new TextView(this);

        amountText.setText(
                (isUdhar ? "+" : "−") + formatRupees(amount)
        );

        amountText.setTextColor(
                isUdhar
                        ? Color.rgb(24, 118, 59)
                        : Color.rgb(33, 90, 180)
        );

        amountText.setTextSize(14);
        amountText.setTypeface(null, Typeface.BOLD);

        row.addView(amountText);

        return row;
    }

    private void openEditCustomer() {
        Cursor cursor = databaseHelper.getCustomer(customerId);

        if (cursor == null) {
            return;
        }

        try {
            if (!cursor.moveToFirst()) {
                Toast.makeText(
                        this,
                        "Customer nahi mila",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            String name = cursor.getString(
                    cursor.getColumnIndexOrThrow("name")
            );

            String mobile = cursor.getString(
                    cursor.getColumnIndexOrThrow("mobile")
            );

            String address = cursor.getString(
                    cursor.getColumnIndexOrThrow("address")
            );

            customerName = name == null ? "" : name;
            customerMobile = mobile == null ? "" : mobile;
            customerAddress = address == null ? "" : address;

        } finally {
            cursor.close();
        }

        Intent intent = new Intent(
                this,
                EditCustomerActivity.class
        );

        intent.putExtra("customer_id", customerId);
        intent.putExtra("customer_name", customerName);
        intent.putExtra("customer_mobile", customerMobile);
        intent.putExtra("customer_address", customerAddress);

        startActivity(intent);
    }

    private void confirmDeleteCustomer() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Customer")
                .setMessage(
                        "Kya aap is customer aur uske saare transactions " +
                                "delete karna chahte hain? Yeh action undo " +
                                "nahi kiya ja sakta."
                )
                .setPositiveButton("Delete", (dialog, which) -> {

                    boolean deleted =
                            databaseHelper.deleteCustomer(customerId);

                    if (!deleted) {
                        Toast.makeText(
                                this,
                                "Customer delete nahi hua",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    Toast.makeText(
                            this,
                            "Customer delete ho gaya. Firebase sync ho raha hai.",
                            Toast.LENGTH_LONG
                    ).show();

                    cloudSyncManager.backupLocalData(
                            new CloudSyncManager.SyncCallback() {
                                @Override
                                public void onSuccess() {
                                    if (!isFinishing()) {
                                        Toast.makeText(
                                                CustomerDetailsActivity.this,
                                                "Firebase sync successful",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                }

                                @Override
                                public void onError(
                                        @NonNull Exception exception
                                ) {
                                    if (!isFinishing()) {
                                        Toast.makeText(
                                                CustomerDetailsActivity.this,
                                                "Cloud sync fail hua. Dobara sync karein.",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                }
                            }
                    );

                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (databaseHelper != null && customerId != -1) {
            loadCustomerDetails();

            if (!isFinishing()) {
                loadRecentTransactions();
            }
        }
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
}
