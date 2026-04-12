package com.example.adaptivesnake

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        findViewById<SnakeGameView>(R.id.snakeGameView).resume()
    }

    override fun onPause() {
        findViewById<SnakeGameView>(R.id.snakeGameView).pause()
        super.onPause()
    }
}
