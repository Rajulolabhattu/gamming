package com.example.adaptivesnake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class SnakeGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val frameHandler = Handler(Looper.getMainLooper())
    private var tickRateMs = 140L
    private var running = false

    private var boardCols = 20
    private var boardRows = 30
    private var cellSizePx = 32f

    private val snake = ArrayDeque<Point>()
    private var direction = Direction.RIGHT
    private var pendingDirection = Direction.RIGHT
    private var food = Point(8, 8)

    private var score = 0
    private var gameOver = false

    private var touchStartX = 0f
    private var touchStartY = 0f

    private val backgroundPaint = Paint().apply { color = Color.parseColor("#101820") }
    private val snakePaint = Paint().apply { color = Color.parseColor("#4ED07A") }
    private val foodPaint = Paint().apply { color = Color.parseColor("#FF6B6B") }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
    }

    private val gameTick = object : Runnable {
        override fun run() {
            if (!running) return
            updateGame()
            invalidate()
            frameHandler.postDelayed(this, tickRateMs)
        }
    }

    init {
        isFocusable = true
        isClickable = true
        resetGame()
    }

    fun resume() {
        if (running) return
        running = true
        frameHandler.post(gameTick)
    }

    fun pause() {
        running = false
        frameHandler.removeCallbacks(gameTick)
    }

    private fun resetGame() {
        snake.clear()
        val startX = boardCols / 2
        val startY = boardRows / 2
        snake.addFirst(Point(startX, startY))
        snake.addLast(Point(startX - 1, startY))
        snake.addLast(Point(startX - 2, startY))
        direction = Direction.RIGHT
        pendingDirection = Direction.RIGHT
        score = 0
        tickRateMs = 140L
        gameOver = false
        spawnFood()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val baseCell = max(18f * resources.displayMetrics.density, min(w, h) / 24f)
        cellSizePx = baseCell
        boardCols = max(14, (w / cellSizePx).toInt())
        boardRows = max(20, (h / cellSizePx).toInt())

        hudPaint.textSize = max(18f * resources.displayMetrics.scaledDensity, min(w, h) / 20f)
        resetGame()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val offsetX = (width - boardCols * cellSizePx) / 2f
        val offsetY = (height - boardRows * cellSizePx) / 2f

        for (segment in snake) {
            val left = offsetX + segment.x * cellSizePx
            val top = offsetY + segment.y * cellSizePx
            canvas.drawRoundRect(
                left + 2,
                top + 2,
                left + cellSizePx - 2,
                top + cellSizePx - 2,
                cellSizePx * 0.2f,
                cellSizePx * 0.2f,
                snakePaint
            )
        }

        val foodLeft = offsetX + food.x * cellSizePx
        val foodTop = offsetY + food.y * cellSizePx
        canvas.drawCircle(
            foodLeft + cellSizePx / 2f,
            foodTop + cellSizePx / 2f,
            cellSizePx * 0.36f,
            foodPaint
        )

        canvas.drawText("Score: $score", offsetX, offsetY - 12f, hudPaint)

        if (gameOver) {
            val centerX = width / 2f
            val centerY = height / 2f
            val gameOverText = "Game Over"
            val restartText = "Tap to restart"
            val gameOverWidth = hudPaint.measureText(gameOverText)
            val restartWidth = hudPaint.measureText(restartText)
            canvas.drawText(gameOverText, centerX - gameOverWidth / 2f, centerY, hudPaint)
            canvas.drawText(
                restartText,
                centerX - restartWidth / 2f,
                centerY + hudPaint.textSize * 1.5f,
                hudPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                if (gameOver) {
                    resetGame()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                if (abs(dx) > abs(dy)) {
                    if (dx > 0) setDirection(Direction.RIGHT) else setDirection(Direction.LEFT)
                } else if (abs(dy) > 6) {
                    if (dy > 0) setDirection(Direction.DOWN) else setDirection(Direction.UP)
                }
            }
        }
        return true
    }

    private fun setDirection(newDirection: Direction) {
        if (!direction.isOpposite(newDirection)) {
            pendingDirection = newDirection
        }
    }

    private fun updateGame() {
        if (gameOver) return

        direction = pendingDirection
        val head = snake.first()
        val next = Point(head.x + direction.dx, head.y + direction.dy)

        if (next.x !in 0 until boardCols || next.y !in 0 until boardRows || snake.any { it == next }) {
            gameOver = true
            return
        }

        snake.addFirst(next)

        if (next == food) {
            score += 10
            tickRateMs = max(70L, tickRateMs - 3)
            spawnFood()
        } else {
            snake.removeLast()
        }
    }

    private fun spawnFood() {
        while (true) {
            val candidate = Point(Random.nextInt(boardCols), Random.nextInt(boardRows))
            if (candidate !in snake) {
                food = candidate
                return
            }
        }
    }

    private enum class Direction(val dx: Int, val dy: Int) {
        UP(0, -1),
        DOWN(0, 1),
        LEFT(-1, 0),
        RIGHT(1, 0);

        fun isOpposite(other: Direction): Boolean {
            return dx + other.dx == 0 && dy + other.dy == 0
        }
    }
}
