
package com.smart_nootes.udhaarapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class AddCustomerActivity extends AppCompatActivity {

    EditText edtCustomerName;
    EditText edtCustomerMobile;
    EditText edtCustomerAddress;

    Button btnSaveCustomer;

    DatabaseHelper databaseHelper;
    CloudSyncManager cloudSyncManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_add_customer);

        edtCustomerName = findViewById(R.id.edtCustomerName);
        edtCustomerMobile = findViewById(R.id.edtCustomerMobile);
        edtCustomerAddress = findViewById(R.id.edtCustomerAddress);

        btnSaveCustomer = findViewById(R.id.btnSaveCustomer);

        databaseHelper = new DatabaseHelper(this);
        cloudSyncManager = new CloudSyncManager(databaseHelper);

        btnSaveCustomer.setOnClickListener(v -> {

            String name = edtCustomerName.getText()
                    .toString().trim();

            String mobile = edtCustomerMobile.getText()
                    .toString().trim();

            String address = edtCustomerAddress.getText()
                    .toString().trim();

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

            SharedPreferences preferences =
                    getSharedPreferences(
                            "SmartKhataSession",
                            MODE_PRIVATE
                    );

            String ownerMobile =
                    preferences.getString("mobile", "");

            if (ownerMobile.isEmpty()) {
                Toast.makeText(
                        AddCustomerActivity.this,
                        "Please login again",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            int userId = databaseHelper.getUserId(ownerMobile);

            if (userId == -1) {
                Toast.makeText(
                        AddCustomerActivity.this,
                        "User not found",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            boolean inserted = databaseHelper.addCustomer(
                    userId,
                    name,
                    mobile,
                    address
            );

            if (inserted) {

                Toast.makeText(
                        AddCustomerActivity.this,
                        "Customer added successfully",
                        Toast.LENGTH_SHORT
                ).show();

                // Upload local data to Firebase.
                cloudSyncManager.backupLocalData(
                        new CloudSyncManager.SyncCallback() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(
                                        AddCustomerActivity.this,
                                        "Cloud backup successful",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }

                            @Override
                            public void onError(
                                    @NonNull Exception e
                            ) {
                                Toast.makeText(
                                        AddCustomerActivity.this,
                                        "Saved locally. Cloud sync failed.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                );

                finish();

            } else {
                Toast.makeText(
                        AddCustomerActivity.this,
                        "Customer could not be added",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}
