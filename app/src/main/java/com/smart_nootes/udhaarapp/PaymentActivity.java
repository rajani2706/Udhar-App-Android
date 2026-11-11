
package com.smart_nootes.udhaarapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class PaymentActivity extends AppCompatActivity {

    TextView txtCustomerName;

    EditText edtAmount;
    EditText edtDescription;

    Button btnSavePayment;

    DatabaseHelper databaseHelper;
    CloudSyncManager cloudSyncManager;

    int customerId;
    String customerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_payment);

        txtCustomerName = findViewById(R.id.txtCustomerName);
        edtAmount = findViewById(R.id.edtAmount);
        edtDescription = findViewById(R.id.edtDescription);
        btnSavePayment = findViewById(R.id.btnSavePayment);

        databaseHelper = new DatabaseHelper(this);

        // Firebase cloud sync manager initialize karo.
        cloudSyncManager = new CloudSyncManager(databaseHelper);

        customerId = getIntent().getIntExtra(
                "customer_id",
                -1
        );

        customerName = getIntent().getStringExtra(
                "customer_name"
        );

        if (customerName != null) {
            txtCustomerName.setText(customerName);
        }

        btnSavePayment.setOnClickListener(v -> {

            String amountText = edtAmount.getText()
                    .toString().trim();

            String description = edtDescription.getText()
                    .toString().trim();

            if (customerId == -1) {
                Toast.makeText(
                        PaymentActivity.this,
                        "Customer not found",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (amountText.isEmpty()) {
                edtAmount.setError("Enter payment amount");
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

            boolean saved = databaseHelper.addTransaction(
                    customerId,
                    "PAYMENT",
                    amount,
                    description
            );

            if (saved) {

                Toast.makeText(
                        PaymentActivity.this,
                        "Payment jama successfully",
                        Toast.LENGTH_SHORT
                ).show();

                // Payment save hote hi Firebase backup trigger karo.
                cloudSyncManager.backupLocalData(
                        new CloudSyncManager.SyncCallback() {

                            @Override
                            public void onSuccess() {
                                Toast.makeText(
                                        PaymentActivity.this,
                                        "Payment synced to Firebase",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }

                            @Override
                            public void onError(
                                    @NonNull Exception e
                            ) {
                                Toast.makeText(
                                        PaymentActivity.this,
                                        "Payment locally saved, cloud sync failed",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                );

                finish();

            } else {
                Toast.makeText(
                        PaymentActivity.this,
                        "Payment save nahi hua",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}
