package com.example.hazardtrackermap;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SignupActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etRealName, etEmail;
    private Button btnSignup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        etRealName = findViewById(R.id.etRealName);
        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignup = findViewById(R.id.btnSignup);

        btnSignup.setOnClickListener(v -> signup());
    }

    private void signup() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String realName = etRealName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty() || realName.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL("http://10.0.2.2/serverside/usersignup.php");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                String postData = "username=" + username + "&password=" + password +
                        "&real_name=" + realName + "&email=" + email;

                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes());
                os.close();

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = br.readLine();
                JSONObject json = new JSONObject(response);

                runOnUiThread(() -> {
                    if (json.optBoolean("success")) {
                        Toast.makeText(this, "Welcome, " + realName, Toast.LENGTH_SHORT).show();

                        // 1. Create the intent
                        Intent intent = new Intent(SignupActivity.this, MainActivity.class);

                        // 2. Pass the real_name from the local variable to MainActivity
                        intent.putExtra("REAL_NAME", realName);

                        // 3. Start the activity and close this one
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, json.optString("message"), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("SignupActivity", "Signup error", e);
                runOnUiThread(() ->
                        Toast.makeText(
                                SignupActivity.this,
                                "Error: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }).start();
    }
}