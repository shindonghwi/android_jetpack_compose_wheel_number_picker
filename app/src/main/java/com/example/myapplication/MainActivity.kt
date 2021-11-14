package com.example.myapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val numberPicker = findViewById<WheelPicker>(R.id.numberPicker)

        numberPicker.setAdapter(DayAdapter())
        numberPicker.setWrapSelectorWheel(true)

    }
}