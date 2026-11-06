package dev.jdtech.jellyfin.presentation.settings

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CE (#211): the About screen's easter egg, phone only (a TV remote can't tap a logo).
 *
 * The Ephyra jellyfish, drawn live instead of from the vector, so it can swim:
 * - it breathes: the bell pulses gently, and now and then it gives a small stroke on its own;
 * - tap it and it swims: the bell contracts, which pushes it up, then relaxes with a flare and it
 *   drifts back down to its spot;
 * - every seventh tap in a row it does a loop;
 * - long-press it to pick it up and drag it around, and let go to fling it.
 *
 * Each of those buzzes, through the system haptics (no vibrate permission needed).
 *
 * The tentacles are chains of points that follow the body with water drag and remember their
 * shape in the logo, so they trail behind when it moves, fan out on each stroke and ripple while it
 * floats. Everything is in the logo's own units (its 160×160 viewport), y pointing down.
 */
@Composable
fun SwimmingJellyfish(modifier: Modifier = Modifier, logoSize: Dp = 100.dp) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // With animations off in the system settings, it's just the logo.
    val animate = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val sim = remember { JellyfishSim() }
    val scope = rememberCoroutineScope()
    var frame by remember { mutableLongStateOf(0L) }
    val bubbleColor = MaterialTheme.colorScheme.primary

    if (animate) {
        LaunchedEffect(sim) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    sim.advance(((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f))
                    last = now
                    frame = now
                }
            }
        }
    }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(logoSize * 1.8f)
                .pointerInput(animate) {
                    if (!animate) return@pointerInput
                    detectTapGestures { offset ->
                        if (sim.hit(toLogo(offset, logoSize.toPx()))) {
                            // A buzz on every stroke; the loop ends with a "ta-da".
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (sim.tap()) {
                                scope.launch {
                                    delay(450)
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                }
                            }
                        }
                    }
                }
                .pointerInput(animate) {
                    if (!animate) return@pointerInput
                    var holding = false
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            holding = sim.hit(toLogo(offset, logoSize.toPx()))
                            if (holding) {
                                sim.grab()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, amount ->
                            if (holding) {
                                change.consume()
                                sim.dragBy(amount / unitPx(logoSize.toPx()))
                            }
                        },
                        onDragEnd = {
                            if (holding) {
                                sim.release()
                                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            }
                        },
                        onDragCancel = { if (holding) sim.release() },
                    )
                }
    ) {
        // Reading the frame makes the canvas redraw on every simulation step.
        @Suppress("UNUSED_EXPRESSION") frame
        val unit = unitPx(logoSize.toPx())
        sim.halfWidth = size.width / 2f / unit
        sim.halfHeight = size.height / 2f / unit
        drawJellyfish(sim, unit, bubbleColor)
    }
}

private const val SHIFT_Y = -8f

/** px per logo unit. The vector draws its 160 units at 95%. */
private fun unitPx(logoSizePx: Float) = logoSizePx / 160f * 0.95f

/** Canvas px to logo units around the home spot (the canvas center). */
private fun androidx.compose.ui.input.pointer.PointerInputScope.toLogo(
    offset: Offset,
    logoSizePx: Float,
): Offset {
    val unit = unitPx(logoSizePx)
    return Offset(
        (offset.x - size.width / 2f) / unit,
        (offset.y - size.height / 2f) / unit - SHIFT_Y,
    )
}

private val Cyan = Color(0xFF4FD8F0)
private val Purple = Color(0xFF9B7CF6)
private val Shine = Color(0xD9E4FBFF)

private class Bubble(
    var x: Float,
    var y: Float,
    val radius: Float,
    val rise: Float,
    val seed: Float,
    var life: Float,
) {
    val maxLife = life
}

/**
 * A tentacle: a chain of points that hangs from the bell (or from the end of its [parent], for the
 * two branches of a forked tentacle).
 */
private class Tentacle(
    /** The shape in the logo, in body space; the first point is where it's attached. */
    val rest: List<Offset>,
    val color: Color,
    val parent: Tentacle? = null,
    val curls: Boolean = false,
    val phase: Float,
) {
    val count = rest.size
    val x = FloatArray(count)
    val y = FloatArray(count)
    val px = FloatArray(count)
    val py = FloatArray(count)
    val segment = FloatArray(count) { i ->
        if (i == 0) 0f else (rest[i] - rest[i - 1]).getDistance()
    }
    /** Where the chain starts along its whole tentacle (0 at the bell), for the ripple. */
    val start = if (parent != null) 0.55f else 0f
    var initialized = false
    val path = Path()
}

private class JellyfishSim {
    var halfWidth = 300f
    var halfHeight = 160f

    // The body, relative to its home spot.
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var angle = 0f
    var spin = 0f
    var angleTarget = 0f

    // How far the bell is contracted: 0 at rest, 1 on a full stroke, below 0 when it flares.
    var contraction = 0f
    var contractionSpeed = 0f
    private var strokeTime = 10f
    private var strokePower = 0f

    private var time = 0f
    val clock: Float
        get() = time

    private var accumulator = 0f
    private var nextIdleStroke = 3f
    private var lastTap = -10f
    private var taps = 0

    var dragging = false
    private var dragX = 0f
    private var dragY = 0f

    val bubbles = ArrayList<Bubble>()
    private val random = Random(1)

    val tentacles: List<Tentacle> = buildList {
        for (side in listOf(-1f, 1f)) {
            val color = if (side < 0) Cyan else Purple
            fun s(x: Float, y: Float) = Offset(x * side, y)
            add(Tentacle(line(s(11f, 6f), s(11f, 80f), 10), color, phase = 0.3f * side))
            val stem = Tentacle(line(s(36f, 6f), s(36f, 46f), 6), color, phase = 1.1f * side)
            add(stem)
            add(
                Tentacle(
                    cubic(s(36f, 46f), s(36f, 58f), s(43f, 62f), s(46f, 76f), 6),
                    color,
                    parent = stem,
                    phase = 1.6f * side,
                )
            )
            add(
                Tentacle(
                    cubic(s(36f, 46f), s(36f, 58f), s(29f, 62f), s(26f, 76f), 6),
                    color,
                    parent = stem,
                    phase = 2.2f * side,
                )
            )
            add(
                Tentacle(
                    cubic(s(58f, 6f), s(58f, 28f), s(72f, 34f), s(77f, 18f), 8),
                    color,
                    curls = true,
                    phase = 2.9f * side,
                )
            )
        }
    }

    fun hit(point: Offset): Boolean = hypot(point.x - x, point.y - (y + 10f)) < 95f

    /** A stroke. Returns true when it's the seventh tap in a row, which loops. */
    fun tap(): Boolean {
        taps = if (time - lastTap < 1.5f) taps + 1 else 1
        lastTap = time
        val loop = taps % 7 == 0
        stroke(if (loop) 1.35f else 1f)
        // Each stroke kicks it a little sideways, alternating, like a real one correcting course.
        val direction = if (taps % 2 == 0) 1f else -1f
        if (loop) {
            angleTarget += 2f * PI.toFloat() * direction
            spin += 9f * direction
            spawnBubbles(14)
        } else {
            spin += (1.2f + random.nextFloat() * 1.4f) * direction
            spawnBubbles(5)
        }
        nextIdleStroke = time + 5f
        return loop
    }

    fun grab() {
        dragging = true
        dragX = x
        dragY = y
    }

    fun dragBy(amount: Offset) {
        dragX = (dragX + amount.x).coerceIn(-halfWidth + 60f, halfWidth - 60f)
        dragY = (dragY + amount.y).coerceIn(-halfHeight + 72f, halfHeight - 40f)
    }

    fun release() {
        dragging = false
        val speed = hypot(vx, vy)
        if (speed > 900f) {
            vx *= 900f / speed
            vy *= 900f / speed
        }
        nextIdleStroke = time + 2f
    }

    private fun stroke(power: Float) {
        strokeTime = 0f
        strokePower = power
    }

    fun advance(dt: Float) {
        accumulator += dt
        while (accumulator >= STEP) {
            step(STEP)
            accumulator -= STEP
        }
    }

    private fun step(dt: Float) {
        time += dt
        strokeTime += dt

        // On its own, it breathes and now and then swims a little.
        if (!dragging && time > nextIdleStroke) {
            stroke(0.3f + random.nextFloat() * 0.2f)
            spin += (random.nextFloat() - 0.5f) * 1.2f
            nextIdleStroke = time + 4f + random.nextFloat() * 4f
        }

        // The bell: a fast contraction, then a slow, bouncy relaxation that overshoots into a flare.
        val contracting = strokeTime < 0.13f
        val breath = 0.05f * (1f + sin(time * 2.2f))
        val target = if (contracting) strokePower else breath
        val stiffness = if (contracting) 900f else 70f
        val damping = 2f * (if (contracting) 0.8f else 0.28f) * sqrt(stiffness)
        contractionSpeed += (stiffness * (target - contraction) - damping * contractionSpeed) * dt
        contraction += contractionSpeed * dt

        if (dragging) {
            val nx = dragX
            val ny = dragY
            vx += ((nx - x) / dt - vx) * 0.25f
            vy += ((ny - y) / dt - vy) * 0.25f
            x = nx
            y = ny
            // It leans into the way it's carried.
            angleTarget = nearestTurn(angleTarget) + (vx * 0.0012f).coerceIn(-0.7f, 0.7f)
        } else {
            // Thrust only while the bell squeezes, along the way the bell points.
            val thrust = if (contractionSpeed > 0f) contractionSpeed * 250f else 0f
            // It coasts after a stroke and then sinks back slowly, like a real one: a weak pull
            // home, and more drag on the way down than on the way up.
            val drag = if (vy > 0f) 3.4f else 1.9f
            // And it wanders a little from side to side while it floats.
            val wander = 7f * sin(time * 0.37f) + 3f * sin(time * 0.91f + 1f)
            val ax = sin(angle) * thrust - 3.5f * x - drag * vx + wander
            val ay = -cos(angle) * thrust - 3.5f * y - drag * vy + 4f * sin(time * 1.3f)
            vx += ax * dt
            vy += ay * dt
            x += vx * dt
            y += vy * dt
            if (abs(angleTarget - nearestTurn(angleTarget)) > 0f && abs(spin) < 0.5f) {
                angleTarget = nearestTurn(angleTarget)
            }
        }
        // Keep it on the screen.
        val minX = -halfWidth + 60f
        val maxX = halfWidth - 60f
        val minY = -halfHeight + 72f
        val maxY = halfHeight - 40f
        if (x < minX || x > maxX) {
            x = x.coerceIn(minX, maxX)
            vx *= -0.3f
        }
        if (y < minY || y > maxY) {
            y = y.coerceIn(minY, maxY)
            vy *= -0.3f
        }

        spin += (-9f * (angle - angleTarget) - 3.2f * spin) * dt
        angle += spin * dt
        // Back to a plain angle once a loop has settled, so the next one starts from zero.
        if (abs(angle - angleTarget) < 0.02f && abs(spin) < 0.05f) {
            val turns = (angleTarget / (2f * PI.toFloat())).toInt()
            angle -= turns * 2f * PI.toFloat()
            angleTarget -= turns * 2f * PI.toFloat()
        }

        tentacles.forEach { stepTentacle(it) }

        val iterator = bubbles.iterator()
        while (iterator.hasNext()) {
            val bubble = iterator.next()
            bubble.life -= dt
            if (bubble.life <= 0f) {
                iterator.remove()
                continue
            }
            bubble.y -= bubble.rise * dt
            bubble.x += sin(time * 5f + bubble.seed) * 14f * dt
        }
    }

    /** Keeps the angle target's whole turns, so leaning while dragging doesn't undo a loop. */
    private fun nearestTurn(value: Float): Float {
        val turn = 2f * PI.toFloat()
        return Math.round(value / turn) * turn
    }

    private fun stepTentacle(tentacle: Tentacle) {
        val anchor =
            tentacle.parent?.let { Offset(it.x[it.count - 1], it.y[it.count - 1]) }
                ?: toWorld(squeezeAttach(tentacle.rest[0]))
        if (!tentacle.initialized) {
            for (i in 0 until tentacle.count) {
                val p = toWorld(restPose(tentacle, i))
                tentacle.x[i] = p.x
                tentacle.y[i] = p.y
                tentacle.px[i] = p.x
                tentacle.py[i] = p.y
            }
            tentacle.initialized = true
        }
        tentacle.x[0] = anchor.x
        tentacle.y[0] = anchor.y
        // Where the rest pose puts the first point, so a branch's pose follows its stem's end.
        val poseOrigin = toWorld(restPose(tentacle, 0))
        val shiftX = anchor.x - poseOrigin.x
        val shiftY = anchor.y - poseOrigin.y
        val last = tentacle.count - 1
        for (i in 1..last) {
            val along = i.toFloat() / last
            val target = toWorld(restPose(tentacle, i))
            // Close to the bell it holds its shape; the tip mostly drifts with the water.
            val hold = 0.11f - 0.085f * along
            val vxi = (tentacle.x[i] - tentacle.px[i]) * WATER
            val vyi = (tentacle.y[i] - tentacle.py[i]) * WATER
            tentacle.px[i] = tentacle.x[i]
            tentacle.py[i] = tentacle.y[i]
            tentacle.x[i] += vxi + (target.x + shiftX - tentacle.x[i]) * hold
            tentacle.y[i] += vyi + (target.y + shiftY - tentacle.y[i]) * hold
        }
        repeat(2) {
            for (i in 1..last) {
                val dx = tentacle.x[i] - tentacle.x[i - 1]
                val dy = tentacle.y[i] - tentacle.y[i - 1]
                val distance = hypot(dx, dy).coerceAtLeast(0.001f)
                val scale = tentacle.segment[i] / distance
                tentacle.x[i] = tentacle.x[i - 1] + dx * scale
                tentacle.y[i] = tentacle.y[i - 1] + dy * scale
            }
        }
    }

    /** The bell's rim pulls in as it contracts, and the tentacles with it. */
    fun squeezeAttach(point: Offset) = Offset(point.x * (1f - 0.26f * contraction), point.y)

    /**
     * Point [i] of a tentacle where it wants to be, in body space: the logo's shape, fanned out by
     * the stroke, rippling, and curled for the short hooked ones.
     */
    private fun restPose(tentacle: Tentacle, i: Int): Offset {
        val origin = tentacle.rest[0]
        val point = tentacle.rest[i]
        val along = tentacle.start + (1f - tentacle.start) * i / (tentacle.count - 1)
        val squeezedOrigin =
            if (tentacle.parent == null) squeezeAttach(origin) else origin
        var dx = point.x - origin.x
        var dy = point.y - origin.y
        if (tentacle.curls) {
            // The hooks curl up on a stroke and sway on their own.
            val curl =
                (0.9f * contraction + 0.22f * sin(time * 1.7f + tentacle.phase)) *
                    if (origin.x < 0f) 1f else -1f
            val c = cos(curl)
            val s = sin(curl)
            val rx = dx * c - dy * s
            dy = dx * s + dy * c
            dx = rx
        } else {
            // A stroke fans them out and pulls them up; a flare lets them hang loose.
            val spread = 1f + 0.45f * contraction
            dx *= spread
            dy *= 1f - 0.16f * contraction
            dx += origin.x * 0.35f * contraction * along
        }
        // A wave that runs down every tentacle, strongest at the tip.
        val amplitude = 4.5f + 6f * abs(contraction)
        dx += amplitude * sin(along * 7f - time * 3.1f + tentacle.phase) * along.pow(1.3f)
        return Offset(squeezedOrigin.x + dx, squeezedOrigin.y + dy)
    }

    /** Body space to the world: turn around the bell's middle, then move with the body. */
    fun toWorld(point: Offset): Offset {
        val px = point.x
        val py = point.y - PIVOT_Y
        val c = cos(angle)
        val s = sin(angle)
        return Offset(px * c - py * s + x, px * s + py * c + PIVOT_Y + y)
    }

    private fun spawnBubbles(count: Int) {
        repeat(count) {
            val side = if (random.nextBoolean()) 1f else -1f
            val rim = toWorld(Offset(side * (40f + random.nextFloat() * 26f), -4f))
            bubbles +=
                Bubble(
                    x = rim.x,
                    y = rim.y,
                    radius = 2.5f + random.nextFloat() * 5f,
                    rise = 50f + random.nextFloat() * 70f,
                    seed = random.nextFloat() * 6f,
                    life = 1.1f + random.nextFloat() * 0.9f,
                )
        }
    }

    companion object {
        const val STEP = 1f / 120f
        const val WATER = 0.955f
        const val PIVOT_Y = 10f
    }
}

private fun line(from: Offset, to: Offset, count: Int) =
    List(count) { i -> from + (to - from) * (i.toFloat() / (count - 1)) }

private fun cubic(p0: Offset, p1: Offset, p2: Offset, p3: Offset, count: Int) =
    List(count) { i ->
        val t = i.toFloat() / (count - 1)
        val u = 1f - t
        p0 * (u * u * u) + p1 * (3f * u * u * t) + p2 * (3f * u * t * t) + p3 * (t * t * t)
    }

private fun DrawScope.drawJellyfish(sim: JellyfishSim, unit: Float, bubbleColor: Color) {
    withTransform({
        translate(size.width / 2f, size.height / 2f + SHIFT_Y * unit)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        sim.bubbles.forEach { bubble ->
            val fade = (bubble.life / bubble.maxLife).coerceIn(0f, 1f)
            drawCircle(
                color = bubbleColor.copy(alpha = 0.5f * fade),
                radius = bubble.radius * (1.4f - 0.4f * fade),
                center = Offset(bubble.x, bubble.y),
                style = Stroke(width = 1.6f),
            )
        }

        val stroke = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        sim.tentacles.forEach { tentacle ->
            val path = tentacle.path
            path.rewind()
            path.moveTo(tentacle.x[0], tentacle.y[0])
            for (i in 1 until tentacle.count - 1) {
                path.quadraticTo(
                    tentacle.x[i],
                    tentacle.y[i],
                    (tentacle.x[i] + tentacle.x[i + 1]) / 2f,
                    (tentacle.y[i] + tentacle.y[i + 1]) / 2f,
                )
            }
            path.lineTo(tentacle.x[tentacle.count - 1], tentacle.y[tentacle.count - 1])
            drawPath(path, tentacle.color, style = stroke)
        }

        withTransform({
            translate(sim.x, sim.y)
            rotate(Math.toDegrees(sim.angle.toDouble()).toFloat(), pivot = Offset(0f, 10f))
        }) {
            val bell = Bell(sim.contraction, sim.clock)
            drawPath(bell.purple, Purple)
            drawPath(bell.cyan, Cyan)
            drawPath(bell.shine, Shine)
        }
    }
}

/**
 * The bell from the logo, squeezed: as it contracts the rim pulls in more than the top and it gets
 * taller; a flare (below 0) does the opposite.
 */
private class Bell(contraction: Float, private val time: Float) {
    private val c = contraction

    /**
     * The bottom edge from [fromX] to [toX], rippling: a real bell's rim flutters, and more so on
     * a stroke.
     */
    private fun Path.rim(fromX: Float, toX: Float) {
        val steps = 8
        val amplitude = 1.6f + 3.5f * abs(c)
        for (i in 1..steps) {
            val x0 = fromX + (toX - fromX) * (i - 0.5f) / steps
            val x1 = fromX + (toX - fromX) * i.toFloat() / steps
            val wave = amplitude * sin(x0 * 0.16f - time * 7f)
            val control = p(x0, -2f).let { Offset(it.x, it.y + wave) }
            val end = p(x1, -2f)
            quadraticTo(control.x, control.y, end.x, end.y)
        }
    }

    private fun p(x: Float, y: Float): Offset {
        val height = ((-y - 2f) / 64f).coerceIn(0f, 1f)
        val squeeze = 1f - 0.24f * c * (1f - height) - 0.05f * c
        return Offset(x * squeeze, -2f + (y + 2f) * (1f + 0.2f * c))
    }

    private fun Path.move(x: Float, y: Float) = p(x, y).let { moveTo(it.x, it.y) }

    private fun Path.curve(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val a = p(x1, y1)
        val b = p(x2, y2)
        val d = p(x3, y3)
        cubicTo(a.x, a.y, b.x, b.y, d.x, d.y)
    }

    val purple =
        Path().apply {
            move(-64f, -2f)
            curve(-64f, -40f, -36f, -66f, 0f, -66f)
            curve(36f, -66f, 64f, -40f, 64f, -2f)
            rim(64f, -64f)
            close()
        }

    val cyan =
        Path().apply {
            move(-64f, -2f)
            curve(-64f, -40f, -36f, -66f, 0f, -66f)
            curve(12f, -66f, 22f, -63f, 30f, -58f)
            curve(6f, -44f, -4f, -22f, 2f, -2f)
            rim(2f, -64f)
            close()
        }

    val shine =
        Path().apply {
            move(-46f, -20f)
            curve(-45f, -40f, -30f, -54f, -10f, -57f)
            curve(-27f, -47f, -38f, -36f, -38f, -20f)
            val a = p(-42f, -15f)
            val b = p(-46f, -20f)
            quadraticTo(a.x, a.y, b.x, b.y)
            close()
        }
}
