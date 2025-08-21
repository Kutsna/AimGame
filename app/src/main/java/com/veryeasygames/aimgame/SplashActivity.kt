package com.veryeasygames.aimgame

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.view.ViewGroup.LayoutParams
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    private lateinit var splashView: SplashView

    // Timing in milliseconds
    private val fadeInDur  = 500L
    private val revealDur  = 1_500L
    private val glow1Dur   = 250L
    private val blowDur    = 1_500L
    private val fadeOutDur = 500L
    private val totalDur   = fadeInDur + revealDur + glow1Dur + blowDur + fadeOutDur

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Create your SplashView at 0 alpha
        splashView = SplashView(this).apply {
            alpha = 0f
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        // 2) Wrap in a black container
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(splashView)
        }
        setContentView(container)

        // 3) Wait for the view to be laid out before animating
        splashView.viewTreeObserver
            .addOnPreDrawListener(object: ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    splashView.viewTreeObserver.removeOnPreDrawListener(this)
                    runAnimations()
                    return true
                }
            })
    }

    private fun runAnimations() {
        // Fade-in from black
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fadeInDur
            addUpdateListener { splashView.alpha = it.animatedValue as Float }
            start()
        }

        // Text reveal (3s)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = revealDur
            startDelay = fadeInDur
            addUpdateListener { splashView.revealProgress = it.animatedValue as Float }
            start()
        }

        // Glow pulse (1s)
        ValueAnimator.ofFloat(0f, 30f).apply {
            duration = glow1Dur
            startDelay = fadeInDur + revealDur
            addUpdateListener { splashView.glowRadius = it.animatedValue as Float }
            start()
        }

        // Blow-up scale (1s)
        ValueAnimator.ofFloat(1f, 1.2f, 1f).apply {
            duration = blowDur
            startDelay = fadeInDur + revealDur + glow1Dur
            addUpdateListener { splashView.textScale = it.animatedValue as Float }
            start()
        }
        // Glow-down simultaneous
        ValueAnimator.ofFloat(30f, 0f).apply {
            duration = blowDur
            startDelay = fadeInDur + revealDur + glow1Dur
            addUpdateListener { splashView.glowRadius = it.animatedValue as Float }
            start()
        }

        // Fade-out to black
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = fadeOutDur
            startDelay = fadeInDur + revealDur + glow1Dur + blowDur
            addUpdateListener { splashView.alpha = it.animatedValue as Float }
            start()
        }

        // Finally, launch MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, totalDur)
    }
}
