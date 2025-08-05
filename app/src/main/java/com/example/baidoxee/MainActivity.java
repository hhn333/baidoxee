package com.example.baidoxee; // đổi thành package của bạn

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    Button btnBatDau;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnBatDau = findViewById(R.id.button); // R.id.button là ID của nút "Bắt đầu"

        btnBatDau.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Chuyển sang TrangChuActivity
                Intent intent = new Intent(MainActivity.this, TrangChuActivity.class);
                startActivity(intent);
            }
        });
    }
}
