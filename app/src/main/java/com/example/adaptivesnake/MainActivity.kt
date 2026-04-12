package com.example.adaptivesnake

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var snakeGameView: SnakeGameView
    private lateinit var scoreText: TextView
    private lateinit var bestScoreText: TextView
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        snakeGameView = findViewById(R.id.snakeGameView)
        scoreText = findViewById(R.id.scoreText)
        bestScoreText = findViewById(R.id.bestScoreText)
        startButton = findViewById(R.id.startButton)
        pauseButton = findViewById(R.id.pauseButton)

        snakeGameView.setGameUiListener { score, bestScore, state ->
            scoreText.text = getString(R.string.score_label, score)
            bestScoreText.text = getString(R.string.best_score_label, bestScore)
            startButton.text = if (state == SnakeGameView.GameState.NOT_STARTED) {
                getString(R.string.start_game)
            } else {
                getString(R.string.restart_game)
            }
            pauseButton.text = if (state == SnakeGameView.GameState.PAUSED) {
                getString(R.string.resume_game)
            } else {
                getString(R.string.pause_game)
            }
            pauseButton.isEnabled = state != SnakeGameView.GameState.NOT_STARTED
        }

        startButton.setOnClickListener {
            snakeGameView.startNewGame()
        }

        pauseButton.setOnClickListener {
            snakeGameView.togglePause()
        }
    }

    override fun onResume() {
        super.onResume()
        snakeGameView.resume()
    }

    override fun onPause() {
        snakeGameView.pause()
        super.onPause()
    }
}
