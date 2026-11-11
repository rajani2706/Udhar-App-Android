
package com.smart_nootes.udhaarapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private EditText edtEmail, edtPassword;
    private Button btnLogin;
    private TextView txtRegister;

    private DatabaseHelper databaseHelper;
    private FirebaseAuth firebaseAuth;
    private CloudSyncManager cloudSyncManager;

    private boolean processingLogin = false;
    private LinearLayout loadingLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        databaseHelper = new DatabaseHelper(this);
        firebaseAuth = FirebaseAuth.getInstance();
        cloudSyncManager = new CloudSyncManager(databaseHelper);

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser != null && currentUser.getEmail() != null) {
            // Existing session: Login form show mat karo.
            showLoadingScreen();
            processingLogin = true;
            restoreAndOpenDashboard();
        } else {
            // No existing session: normal Login screen.
            showLoginScreen();
        }
    }

    private void showLoadingScreen() {
        loadingLayout = new LinearLayout(this);
        loadingLayout.setOrientation(LinearLayout.VERTICAL);
        loadingLayout.setGravity(Gravity.CENTER);
        loadingLayout.setPadding(32, 32, 32, 32);
        loadingLayout.setBackgroundColor(0xFFF5F7FA);

        ProgressBar progressBar = new ProgressBar(this);
        loadingLayout.addView(progressBar);

        TextView message = new TextView(this);
        message.setText("Restoring your shop data...");
        message.setTextSize(16);
        message.setTextColor(0xFF333333);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 24, 0, 0);
        loadingLayout.addView(message);

        setContentView(loadingLayout);
    }

    private void showLoginScreen() {
        setContentView(R.layout.activity_main);

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        txtRegister = findViewById(R.id.txtRegister);

        btnLogin.setEnabled(true);

        btnLogin.setOnClickListener(v -> loginUser());

        txtRegister.setOnClickListener(v -> {
            startActivity(new Intent(
                    MainActivity.this,
                    RegisterActivity.class
            ));
        });
    }

    private void loginUser() {
        if (processingLogin) {
            return;
        }

        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString();

        if (email.isEmpty()) {
            edtEmail.setError("Enter email address");
            edtEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Enter a valid email address");
            edtEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            edtPassword.setError("Enter password");
            edtPassword.requestFocus();
            return;
        }

        processingLogin = true;
        btnLogin.setEnabled(false);

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (!task.isSuccessful()) {
                        processingLogin = false;
                        btnLogin.setEnabled(true);

                        Toast.makeText(
                                MainActivity.this,
                                "Login failed. Check email and password.",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    FirebaseUser user = firebaseAuth.getCurrentUser();

                    if (user == null || user.getEmail() == null) {
                        processingLogin = false;
                        btnLogin.setEnabled(true);

                        Toast.makeText(
                                MainActivity.this,
                                "Unable to get account details.",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    // Successful login ke baad loading screen dikhao.
                    showLoadingScreen();
                    restoreAndOpenDashboard();
                });
    }

    private void restoreAndOpenDashboard() {
        cloudSyncManager.restoreCloudData(
                new CloudSyncManager.SyncCallback() {

                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            FirebaseUser user = firebaseAuth.getCurrentUser();

                            if (user == null || user.getEmail() == null) {
                                handleRestoreError(
                                        "Firebase session not available."
                                );
                                return;
                            }

                            String mobile = databaseHelper.getMobileByEmail(
                                    user.getEmail()
                            );

                            if (mobile == null || mobile.trim().isEmpty()) {
                                handleRestoreError(
                                        "Shop profile restore nahi hua."
                                );
                                return;
                            }

                            getSharedPreferences(
                                    "SmartKhataSession",
                                    MODE_PRIVATE
                            ).edit()
                                    .putString("mobile", mobile)
                                    .apply();

                            Toast.makeText(
                                    MainActivity.this,
                                    "Cloud data restored successfully.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            openDashboard(mobile);
                        });
                    }

                    @Override
                    public void onError(Exception exception) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            String message = exception.getMessage();

                            if (message == null || message.trim().isEmpty()) {
                                message = "Please try again.";
                            }

                            handleRestoreError("Restore failed: " + message);
                        });
                    }
                }
        );
    }

    private void handleRestoreError(String message) {
        processingLogin = false;

        // Session ko sign out nahi karte.
        // Error ke baad loading screen par nahi chhodna.
        showRestoreErrorScreen(message);
    }

    private void showRestoreErrorScreen(String message) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(32, 32, 32, 32);
        layout.setBackgroundColor(0xFFF5F7FA);

        TextView errorText = new TextView(this);
        errorText.setText(message);
        errorText.setTextSize(16);
        errorText.setTextColor(0xFF333333);
        errorText.setGravity(Gravity.CENTER);
        layout.addView(errorText);

        Button retryButton = new Button(this);
        retryButton.setText("RETRY");
        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        buttonParams.topMargin = 24;
        layout.addView(retryButton, buttonParams);

        Button logoutButton = new Button(this);
        logoutButton.setText("LOG OUT");
        layout.addView(logoutButton);

        retryButton.setOnClickListener(v -> {
            processingLogin = true;
            showLoadingScreen();
            restoreAndOpenDashboard();
        });

        logoutButton.setOnClickListener(v -> {
            firebaseAuth.signOut();
            processingLogin = false;
            showLoginScreen();
        });

        setContentView(layout);
    }

    private void openDashboard(String mobile) {
        String ownerName = databaseHelper.getOwnerName(mobile);
        String shopName = databaseHelper.getShopName(mobile);

        Intent intent = new Intent(
                MainActivity.this,
                DashboardActivity.class
        );

        intent.putExtra("owner_name", ownerName);
        intent.putExtra("shop_name", shopName);

        startActivity(intent);
        finish();
    }
}
