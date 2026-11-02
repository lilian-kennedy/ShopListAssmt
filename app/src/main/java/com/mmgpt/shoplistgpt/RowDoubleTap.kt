package com.mmgpt.shoplistgpt

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.RecyclerView

/** Calls [onRow] only when both taps of a double tap land on the same row. */
fun RecyclerView.setOnRowDoubleTap(onRow: (position: Int) -> Unit) {
    fun rowAt(e: MotionEvent): Int =
        findChildViewUnder(e.x, e.y)?.let { getChildAdapterPosition(it) } ?: RecyclerView.NO_POSITION

    val detector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
        private var firstTapRow = RecyclerView.NO_POSITION

        // GestureDetector passes the first tap here; the second tap arrives in onDoubleTapEvent.
        override fun onDoubleTap(e: MotionEvent): Boolean {
            firstTapRow = rowAt(e)
            return false
        }

        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_DOWN) return false
            val secondTapRow = rowAt(e)
            if (secondTapRow == RecyclerView.NO_POSITION || secondTapRow != firstTapRow) return false
            onRow(secondTapRow)
            return true
        }
    })

    setOnTouchListener { _, event ->
        detector.onTouchEvent(event)
        false
    }
}
