
package com.smart_nootes.udhaarapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class EditCustomerActivity extends AppCompatActivity {

    EditText edtCustomerName;
    EditText edtCustomerMobile;
    EditText edtCustomerAddress;

    Button btnUpdateCustomer;

    DatabaseHelper databaseHelper;
    CloudSyncManager cloudSyncManager;

    int customerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_edit_customer);

        edtCustomerName = findViewById(R.id.edtCustomerName);
        edtCustomerMobile = findViewById(R.id.edtCustomerMobile);
        edtCustomerAddress = findViewById(R.id.edtCustomerAddress);

        btnUpdateCustomer = findViewById(R.id.btnUpdateCustomer);

        databaseHelper = new DatabaseHelper(this);
        cloudSyncManager = new CloudSyncManager(databaseHelper);

        customerId = getIntent().getIntExtra(
                "customer_id",
                -1
        );

        edtCustomerName.setText(
                getIntent().getStringExtra("customer_name")
        );

        edtCustomerMobile.setText(
                getIntent().getStringExtra("customer_mobile")
        );

        edtCustomerAddress.setText(
                getIntent().getStringExtra("customer_address")
        );

        btnUpdateCustomer.setOnClickListener(v -> {

            String name = edtCustomerName.getText()
                    .toString().trim();

            String mobile = edtCustomerMobile.getText()
                    .toString().trim();

            String address = edtCustomerAddress.getText()
                    .toString().trim();

            if (customerId == -1) {
                Toast.makeText(
                        EditCustomerActivity.this,
                        "Customer not found",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (name.isEmpty()) {
                edtCustomerName.setError("Enter customer name");
                edtCustomerName.requestFocus();
                return;
            }

            if (!mobile.isEmpty() &&
                    !mobile.matches("[0-9]{10}")) {
                edtCustomerMobile.setError(
                        "Enter valid 10 digit mobile number"
                );
                edtCustomerMobile.requestFocus();
                return;
            }

            boolean updated = databaseHelper.updateCustomer(
                    customerId,
                    name,
                    mobile,
                    address
            );

            if (updated) {

                Toast.makeText(
                        EditCustomerActivity.this,
                        "Customer updated successfully",
                        Toast.LENGTH_SHORT
                ).show();

                // Customer update hote hi Firebase backup trigger karo.
                cloudSyncManager.backupLocalData(
                        new CloudSyncManager.SyncCallback() {

                            @Override
                            public void onSuccess() {
                                Toast.makeText(
                                        EditCustomerActivity.this,
                                        "Customer synced to Firebase",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }

                            @Override
                            public void onError(
                                    @NonNull Exception e
                            ) {
                                Toast.makeText(
                                        EditCustomerActivity.this,
                                        "Updated locally. Cloud sync failed.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                );

                finish();

            } else {
                Toast.makeText(
                        EditCustomerActivity.this,
                        "Could not update customer",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}
