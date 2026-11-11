package com.smart_nootes.udhaarapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class CustomerListActivity extends AppCompatActivity {

    private EditText edtSearchCustomer;
    private TextView txtCustomerCount;
    private ListView listCustomers;
    private Button btnAddCustomer;

    private DatabaseHelper databaseHelper;
    private CloudSyncManager cloudSyncManager;

    private ArrayList<Customer> customerList;
    private ArrayList<Customer> allCustomers;

    private CustomerAdapter adapter;

    private int userId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_customer_list);

        // Initialize views
        edtSearchCustomer = findViewById(R.id.edtSearchCustomer);
        txtCustomerCount = findViewById(R.id.txtCustomerCount);
        listCustomers = findViewById(R.id.listCustomers);
        btnAddCustomer = findViewById(R.id.btnAddCustomer);

        // Initialize database and cloud sync
        databaseHelper = new DatabaseHelper(this);
        cloudSyncManager = new CloudSyncManager(databaseHelper);

        customerList = new ArrayList<>();
        allCustomers = new ArrayList<>();

        // Get logged-in user's mobile number
        SharedPreferences preferences = getSharedPreferences(
                "SmartKhataSession",
                MODE_PRIVATE
        );

        String mobile = preferences.getString("mobile", "");

        if (mobile == null || mobile.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Please login first",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
            return;
        }

        // Get local user ID
        userId = databaseHelper.getUserId(mobile);

        if (userId < 0) {

            Toast.makeText(
                    this,
                    "User not found. Please login again.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        // Initialize adapter
        adapter = new CustomerAdapter();
        listCustomers.setAdapter(adapter);

        // Load locally saved customers
        loadCustomers();

        // Search customers by name or mobile
        edtSearchCustomer.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
                // No action required
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                searchCustomer(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No action required
            }
        });

        // Open Add Customer screen
        btnAddCustomer.setOnClickListener(v -> {

            Intent intent = new Intent(
                    CustomerListActivity.this,
                    AddCustomerActivity.class
            );

            startActivity(intent);
        });

        // Open selected customer's screen
        listCustomers.setOnItemClickListener(
                (parent, view, position, id) -> {

                    if (position < 0 || position >= customerList.size()) {
                        return;
                    }

                    Customer customer = customerList.get(position);

                    String openMode =
                            getIntent().getStringExtra("open_mode");

                    Intent intent;

                    if ("udhar".equals(openMode)) {

                        intent = new Intent(
                                CustomerListActivity.this,
                                AddUdharActivity.class
                        );

                    } else if ("payment".equals(openMode)) {

                        intent = new Intent(
                                CustomerListActivity.this,
                                PaymentActivity.class
                        );

                    } else if ("hisab".equals(openMode)) {

                        intent = new Intent(
                                CustomerListActivity.this,
                                TransactionHistoryActivity.class
                        );

                    } else {

                        intent = new Intent(
                                CustomerListActivity.this,
                                CustomerDetailsActivity.class
                        );
                    }

                    // Pass selected customer details
                    intent.putExtra("customer_id", customer.id);
                    intent.putExtra("customer_name", customer.name);
                    intent.putExtra("customer_mobile", customer.mobile);
                    intent.putExtra("customer_address", customer.address);

                    startActivity(intent);
                }
        );
    }

    // Start real-time sync whenever this screen becomes active
    @Override
    protected void onStart() {
        super.onStart();

        if (cloudSyncManager != null && userId >= 0) {

            cloudSyncManager.startRealtimeSync(() -> {

                // Firestore callback may update the screen only
                // while this Activity is still active.
                if (!isFinishing() && !isDestroyed()) {
                    runOnUiThread(this::loadCustomers);
                }
            });
        }
    }

    // Stop listeners when this screen is no longer active
    @Override
    protected void onStop() {

        if (cloudSyncManager != null) {
            cloudSyncManager.stopRealtimeSync();
        }

        super.onStop();
    }

    // Load customers belonging to the logged-in user
    private void loadCustomers() {

        if (databaseHelper == null
                || allCustomers == null
                || customerList == null
                || userId < 0) {
            return;
        }

        allCustomers.clear();
        customerList.clear();

        Cursor cursor = null;

        try {

            cursor = databaseHelper.getCustomers(userId);

            if (cursor != null) {

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

                    Customer customer = new Customer(
                            id,
                            name,
                            mobile,
                            address
                    );

                    allCustomers.add(customer);
                }
            }

        } catch (Exception exception) {

            Toast.makeText(
                    this,
                    "Unable to load customers",
                    Toast.LENGTH_SHORT
            ).show();

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }

        // Preserve the current search text after refreshing
        String searchText = edtSearchCustomer == null
                ? ""
                : edtSearchCustomer.getText().toString();

        searchCustomer(searchText);
    }

    // Search customers by name or mobile
    private void searchCustomer(String searchText) {

        if (customerList == null || allCustomers == null) {
            return;
        }

        customerList.clear();

        String search = searchText == null
                ? ""
                : searchText.toLowerCase().trim();

        for (Customer customer : allCustomers) {

            String name = customer.name == null
                    ? ""
                    : customer.name.toLowerCase();

            String mobile = customer.mobile == null
                    ? ""
                    : customer.mobile;

            if (name.contains(search) || mobile.contains(search)) {
                customerList.add(customer);
            }
        }

        updateCustomerCount();

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    // Update customer count
    private void updateCustomerCount() {

        if (txtCustomerCount != null && customerList != null) {

            txtCustomerCount.setText(
                    customerList.size() + " Customers"
            );
        }
    }

    // Refresh list when returning to this screen
    @Override
    protected void onResume() {
        super.onResume();

        if (databaseHelper != null
                && userId >= 0
                && customerList != null
                && allCustomers != null) {

            loadCustomers();
        }
    }

    // Customer model
    private static class Customer {

        int id;
        String name;
        String mobile;
        String address;

        Customer(
                int id,
                String name,
                String mobile,
                String address
        ) {
            this.id = id;
            this.name = name;
            this.mobile = mobile;
            this.address = address;
        }
    }

    // Customer list adapter
    private class CustomerAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return customerList.size();
        }

        @Override
        public Object getItem(int position) {
            return customerList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return customerList.get(position).id;
        }

        @Override
        public View getView(
                int position,
                View convertView,
                ViewGroup parent
        ) {

            if (convertView == null) {

                convertView = LayoutInflater.from(
                        CustomerListActivity.this
                ).inflate(
                        R.layout.customer_list_item,
                        parent,
                        false
                );
            }

            TextView txtName =
                    convertView.findViewById(R.id.txtCustomerName);

            TextView txtMobile =
                    convertView.findViewById(R.id.txtCustomerMobile);

            TextView txtAddress =
                    convertView.findViewById(R.id.txtCustomerAddress);

            Customer customer = customerList.get(position);

            txtName.setText(
                    customer.name == null
                            ? ""
                            : customer.name
            );

            if (customer.mobile == null
                    || customer.mobile.trim().isEmpty()) {

                txtMobile.setText("Mobile number not added");

            } else {

                txtMobile.setText(customer.mobile);
            }

            if (customer.address == null
                    || customer.address.trim().isEmpty()) {

                txtAddress.setText("Address not added");

            } else {

                txtAddress.setText(customer.address);
            }

            return convertView;
        }
    }
}