
package com.smart_nootes.udhaarapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class AddUdharActivity extends AppCompatActivity {

    TextView txtCustomerName;

    EditText edtAmount;
    EditText edtDescription;

    Button btnSaveUdhar;

    DatabaseHelper databaseHelper;
    CloudSyncManager cloudSyncManager;

    int customerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_add_udhar);

        txtCustomerName = findViewById(R.id.txtCustomerName);
        edtAmount = findViewById(R.id.edtAmount);
        edtDescription = findViewById(R.id.edtDescription);
        btnSaveUdhar = findViewById(R.id.btnSaveUdhar);

        databaseHelper = new DatabaseHelper(this);
        cloudSyncManager = new CloudSyncManager(databaseHelper);

        customerId = getIntent().getIntExtra(
                "customer_id",
                -1
        );

        String customerName = getIntent().getStringExtra(
                "customer_name"
        );

        if (customerName != null) {
            txtCustomerName.setText("Customer: " + customerName);
        }

        btnSaveUdhar.setOnClickListener(v -> {

            String amountText = edtAmount.getText()
                    .toString().trim();

            String description = edtDescription.getText()
                    .toString().trim();

            if (amountText.isEmpty()) {
                edtAmount.setError("Enter amount");
                edtAmount.requestFocus();
                return;
            }

            double amount;

            try {
                amount = Double.parseDouble(amountText);
            } catch (Exception e) {
                edtAmount.setError("Enter valid amount");
                edtAmount.requestFocus();
                return;
            }

            if (amount <= 0) {
                edtAmount.setError(
                        "Amount must be greater than 0"
                );
                edtAmount.requestFocus();
                return;
            }

            if (customerId == -1) {
                Toast.makeText(
                        AddUdharActivity.this,
                        "Customer not found",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            boolean inserted = databaseHelper.addTransaction(
                    customerId,
                    "UDHAR",
                    amount,
                    description
            );

            if (inserted) {

                Toast.makeText(
                        AddUdharActivity.this,
                        "Udhar added successfully",
                        Toast.LENGTH_SHORT
                ).show();

                // Upload local data to Firebase.
                cloudSyncManager.backupLocalData(
                        new CloudSyncManager.SyncCallback() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(
                                        AddUdharActivity.this,
                                        "Udhar cloud backup successful",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }

                            @Override
                            public void onError(
                                    @NonNull Exception e
                            ) {
                                Toast.makeText(
                                        AddUdharActivity.this,
                                        "Saved locally. Cloud sync failed.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                );

                finish();

            } else {
                Toast.makeText(
                        AddUdharActivity.this,
                        "Could not add udhar",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}
