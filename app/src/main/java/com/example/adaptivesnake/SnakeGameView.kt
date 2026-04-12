package com.example.adaptivesnake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
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

    enum class GameState {
        NOT_STARTED,
        RUNNING,
        PAUSED,
        GAME_OVER
    }

    private val frameHandler = Handler(Looper.getMainLooper())
    private var tickRateMs = 140L
    private var running = false
    private var state = GameState.NOT_STARTED

    private var boardCols = 20
    private var boardRows = 30
    private var cellSizePx = 32f

    private val snake = ArrayDeque<Point>()
    private var direction = Direction.RIGHT
    private var pendingDirection = Direction.RIGHT
    private var food = Point(8, 8)

    private var score = 0
    private var bestScore = 0
    private var gameOver = false

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isInitialized = false

    private val prefs by lazy {
        context.getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)
    }
    private var gameUiListener: ((score: Int, bestScore: Int, state: GameState) -> Unit)? = null

    private val backgroundPaint = Paint().apply { color = Color.parseColor("#09131E") }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1B3550")
        strokeWidth = 1f
    }
    private val snakePaint = Paint().apply { color = Color.parseColor("#37C978") }
    private val snakeHeadPaint = Paint().apply { color = Color.parseColor("#58F3A1") }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val foodPaint = Paint().apply { color = Color.parseColor("#FF7B8D") }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
    }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B2081119")
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
        bestScore = prefs.getInt("best_score", 0)
        resetGame()
        updateUi()
    }

    fun resume() {
        if (state == GameState.PAUSED) {
            running = true
            state = GameState.RUNNING
        }
        if (state != GameState.RUNNING || running) return
        running = true
        frameHandler.post(gameTick)
        updateUi()
    }

    fun pause() {
        if (state == GameState.RUNNING) {
            state = GameState.PAUSED
        }
        running = false
        frameHandler.removeCallbacks(gameTick)
        updateUi()
    }

    fun startNewGame() {
        if (!isInitialized) return
        resetGame()
        state = GameState.RUNNING
        if (!running) {
            running = true
            frameHandler.post(gameTick)
        }
        updateUi()
    }

    fun togglePause() {
        when (state) {
            GameState.RUNNING -> pause()
            GameState.PAUSED -> {
                state = GameState.RUNNING
                if (!running) {
                    running = true
                    frameHandler.post(gameTick)
                }
                updateUi()
            }
            else -> Unit
        }
    }

    fun setGameUiListener(listener: (score: Int, bestScore: Int, state: GameState) -> Unit) {
        gameUiListener = listener
        updateUi()
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
        updateUi()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val baseCell = max(18f * resources.displayMetrics.density, min(w, h) / 24f)
        cellSizePx = baseCell
        boardCols = max(14, (w / cellSizePx).toInt())
        boardRows = max(20, (h / cellSizePx).toInt())

        hudPaint.textSize = max(18f * resources.displayMetrics.scaledDensity, min(w, h) / 20f)
        resetGame()
        state = GameState.NOT_STARTED
        running = false
        frameHandler.removeCallbacks(gameTick)
        isInitialized = true
        updateUi()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val offsetX = (width - boardCols * cellSizePx) / 2f
        val offsetY = (height - boardRows * cellSizePx) / 2f

        for (col in 0..boardCols) {
            val x = offsetX + col * cellSizePx
            canvas.drawLine(x, offsetY, x, offsetY + boardRows * cellSizePx, gridPaint)
        }
        for (row in 0..boardRows) {
            val y = offsetY + row * cellSizePx
            canvas.drawLine(offsetX, y, offsetX + boardCols * cellSizePx, y, gridPaint)
        }

        val snakeHead = snake.firstOrNull()
        for (segment in snake) {
            val left = offsetX + segment.x * cellSizePx
            val top = offsetY + segment.y * cellSizePx
            val paint = if (segment == snakeHead) snakeHeadPaint else snakePaint
            canvas.drawRoundRect(
                left + 2,
                top + 2,
                left + cellSizePx - 2,
                top + cellSizePx - 2,
                cellSizePx * 0.2f,
                cellSizePx * 0.2f,
                paint
            )
        }
        drawSnakeEyes(canvas, snakeHead, offsetX, offsetY)

        val foodLeft = offsetX + food.x * cellSizePx
        val foodTop = offsetY + food.y * cellSizePx
        val foodRect = RectF(
            foodLeft + cellSizePx * 0.18f,
            foodTop + cellSizePx * 0.18f,
            foodLeft + cellSizePx * 0.82f,
            foodTop + cellSizePx * 0.82f
        )
        canvas.drawOval(foodRect, foodPaint)

        if (state == GameState.NOT_STARTED || state == GameState.PAUSED || gameOver) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
            val centerX = width / 2f
            val centerY = height / 2f
            val title = when (state) {
                GameState.NOT_STARTED -> "Neon Snake"
                GameState.PAUSED -> "Paused"
                GameState.GAME_OVER -> "Game Over"
                else -> ""
            }
            val subtitle = when (state) {
                GameState.NOT_STARTED -> "Hit Start to begin"
                GameState.PAUSED -> "Tap Resume to continue"
                GameState.GAME_OVER -> "Try again with Restart"
                else -> ""
            }
            if (title.isNotBlank()) {
                val titleWidth = hudPaint.measureText(title)
                val subtitleWidth = hudPaint.measureText(subtitle)
                canvas.drawText(title, centerX - titleWidth / 2f, centerY, hudPaint)
                canvas.drawText(
                    subtitle,
                    centerX - subtitleWidth / 2f,
                    centerY + hudPaint.textSize * 1.5f,
                    hudPaint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                if (gameOver) {
                    startNewGame()
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
            running = false
            frameHandler.removeCallbacks(gameTick)
            state = GameState.GAME_OVER
            updateUi()
            return
        }

        snake.addFirst(next)

        if (next == food) {
            score += 10
            if (score > bestScore) {
                bestScore = score
                prefs.edit().putInt("best_score", bestScore).apply()
            }
            tickRateMs = max(70L, tickRateMs - 3)
            spawnFood()
            updateUi()
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

    private fun drawSnakeEyes(canvas: Canvas, head: Point?, offsetX: Float, offsetY: Float) {
        if (head == null) return
        val left = offsetX + head.x * cellSizePx
        val top = offsetY + head.y * cellSizePx
        val eyeRadius = cellSizePx * 0.07f
        val horizontalEyeY = top + cellSizePx * 0.3f
        val verticalLeftEyeX = left + cellSizePx * 0.3f
        val verticalRightEyeX = left + cellSizePx * 0.7f

        when (direction) {
            Direction.UP, Direction.DOWN -> {
                canvas.drawCircle(verticalLeftEyeX, horizontalEyeY, eyeRadius, eyePaint)
                canvas.drawCircle(verticalRightEyeX, horizontalEyeY, eyeRadius, eyePaint)
            }

            Direction.LEFT -> {
                canvas.drawCircle(left + cellSizePx * 0.3f, top + cellSizePx * 0.35f, eyeRadius, eyePaint)
                canvas.drawCircle(left + cellSizePx * 0.3f, top + cellSizePx * 0.65f, eyeRadius, eyePaint)
            }

            Direction.RIGHT -> {
                canvas.drawCircle(left + cellSizePx * 0.7f, top + cellSizePx * 0.35f, eyeRadius, eyePaint)
                canvas.drawCircle(left + cellSizePx * 0.7f, top + cellSizePx * 0.65f, eyeRadius, eyePaint)
            }
        }
    }

    private fun updateUi() {
        gameUiListener?.invoke(score, bestScore, state)
        invalidate()
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
