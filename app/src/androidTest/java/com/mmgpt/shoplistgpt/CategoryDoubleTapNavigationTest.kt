package com.mmgpt.shoplistgpt

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec: double-tapping a category row opens that category's page. If the two taps of a
 * quick double tap land on different rows, the app must not navigate at all.
 *
 * Plan mode pages: 0 = category list, i + 1 = category i.
 * Shop mode pages: i = category i, last = summary list.
 */
@RunWith(AndroidJUnit4::class)
class CategoryDoubleTapNavigationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefs get() =
        instrumentation.targetContext.getSharedPreferences("state", Context.MODE_PRIVATE)

    private var names = listOf("Alpha", "Bravo", "Charlie", "Delta")
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity

    @Before
    fun clearState() {
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        prefs.edit().clear().commit()
    }

    // ---------- Plan mode ----------

    @Test
    fun plan_doubleTapOnEachRow_opensThatCategory() {
        launchPlanMode()
        names.forEachIndexed { index, name ->
            doubleTap(name, name)
            assertEquals("Plan: double tap on $name", index + 1, currentPage())
            showPage(0)
        }
    }

    @Test
    fun plan_doubleTapOffCentreOfSameRow_opensThatCategory() {
        launchPlanMode()
        doubleTap("Charlie", "Charlie", secondTapDx = 40)
        assertEquals(3, currentPage())
    }

    @Test
    fun plan_tapsOnDifferentRows_doNotNavigate() {
        launchPlanMode()
        doubleTap("Bravo", "Alpha")
        assertEquals("tap Bravo then Alpha", 0, currentPage())
        doubleTap("Alpha", "Bravo")
        assertEquals("tap Alpha then Bravo", 0, currentPage())
    }

    @Test
    fun plan_tapsOnAdjacentRows_doNotNavigate() {
        launchPlanMode()
        doubleTap("Charlie", "Delta")
        assertEquals(0, currentPage())
    }

    @Test
    fun plan_doubleTapOnLastRowOfScrolledList_opensThatCategory() {
        names = (1..12).map { "Cat%02d".format(it) }
        launchPlanMode()
        scrollListToBottom()
        doubleTap("Cat12", "Cat12")
        assertEquals(12, currentPage())
    }

    @Test
    fun plan_tapsOnDifferentRowsOfScrolledList_doNotNavigate() {
        names = (1..12).map { "Cat%02d".format(it) }
        launchPlanMode()
        scrollListToBottom()
        doubleTap("Cat11", "Cat12")
        assertEquals(0, currentPage())
    }

    // ---------- Shop mode (summary page) ----------

    @Test
    fun shop_doubleTapOnEachSummaryRow_opensThatCategory() {
        launchShopSummary()
        names.forEachIndexed { index, name ->
            doubleTap(name, name)
            assertEquals("Shop: double tap on $name", index, currentPage())
            showPage(names.size)
        }
    }

    @Test
    fun shop_doubleTapOffCentreOfSameRow_opensThatCategory() {
        launchShopSummary()
        doubleTap("Charlie", "Charlie", secondTapDx = 40)
        assertEquals(2, currentPage())
    }

    @Test
    fun shop_tapsOnDifferentRows_doNotNavigate() {
        launchShopSummary()
        doubleTap("Alpha", "Bravo")
        assertEquals("tap Alpha then Bravo", names.size, currentPage())
        doubleTap("Charlie", "Bravo")
        assertEquals("tap Charlie then Bravo", names.size, currentPage())
    }

    @Test
    fun shop_tapsOnAdjacentRows_doNotNavigate() {
        launchShopSummary()
        doubleTap("Bravo", "Charlie")
        assertEquals(names.size, currentPage())
    }

    // ---------- Setup helpers ----------

    private fun seedCategories() {
        val cats = JSONArray()
        names.forEachIndexed { i, heading ->
            cats.put(
                JSONObject()
                    .put("id", i.toLong())
                    .put("heading", heading)
                    .put("items", JSONArray())
            )
        }
        prefs.edit().putString("state", JSONObject().put("categories", cats).toString()).commit()
    }

    private fun launchPlanMode() {
        seedCategories()
        launchActivity()
        onMain { activity.findViewById<View>(R.id.btnPlan).performClick() }
        awaitRow(names.first())
    }

    private fun launchShopSummary() {
        seedCategories()
        launchActivity()
        onMain { activity.findViewById<View>(R.id.btnShop).performClick() }
        showPage(names.size)
    }

    private fun launchActivity() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity = it }
    }

    private fun showPage(page: Int) {
        awaitPagerSettled()
        onMain { pager().setCurrentItem(page, false) }
        awaitPagerSettled()
        awaitRow(names.first())
    }

    private fun awaitPagerSettled() {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (onMain { pager().scrollState } != ViewPager2.SCROLL_STATE_IDLE) {
            if (SystemClock.uptimeMillis() > deadline) fail("Pager never settled")
            SystemClock.sleep(50)
        }
    }

    private fun scrollListToBottom() {
        onMain {
            val scroll = visibleList()!!.parent.let { it as? NestedScrollView ?: it.parent as NestedScrollView }
            scroll.scrollTo(0, scroll.getChildAt(0).height)
        }
        instrumentation.waitForIdleSync()
    }

    // ---------- Touch injection ----------

    /** Taps [first] then [second] 100 ms apart, as one gesture so main-thread lag cannot break it. */
    private fun doubleTap(first: String, second: String, secondTapDx: Int = 0) {
        awaitPagerSettled()
        awaitRow(first)
        val (p1, p2) = onMain {
            val list = visibleList()!!
            val a = centreOf(list, first)
            val b = centreOf(list, second)
            a to (b.first + secondTapDx to b.second)
        }
        val start = SystemClock.uptimeMillis()
        onMain {
            val decor = activity.window.decorView
            tap(decor, p1.first.toFloat(), p1.second.toFloat(), start)
            tap(decor, p2.first.toFloat(), p2.second.toFloat(), start + 100)
        }
        instrumentation.waitForIdleSync()
        awaitPagerSettled()
    }

    private fun tap(target: View, x: Float, y: Float, downAt: Long) {
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to downAt, MotionEvent.ACTION_UP to downAt + 30)) {
            val event = MotionEvent.obtain(downAt, time, action, x, y, 0)
            target.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    // ---------- View lookup ----------

    private fun currentPage(): Int = onMain { pager().currentItem }

    private fun pager(): ViewPager2 = activity.findViewById(R.id.viewPager)

    /** The category list RecyclerView of the page currently on screen. */
    private fun visibleList(): RecyclerView? {
        val found = mutableListOf<RecyclerView>()
        fun walk(v: View) {
            if (v is RecyclerView && v.id == R.id.recyclerView && v.isShown) {
                val r = android.graphics.Rect()
                if (v.getGlobalVisibleRect(r) && r.width() > v.resources.displayMetrics.widthPixels / 2) found += v
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(activity.window.decorView)
        return found.firstOrNull()
    }

    private fun centreOf(list: RecyclerView, text: String): Pair<Int, Int> {
        val label = checkNotNull(findLabel(list, text)) { "No row labelled '$text' in the visible list" }
        val loc = IntArray(2)
        label.getLocationInWindow(loc)
        return loc[0] + label.width / 2 to loc[1] + label.height / 2
    }

    private fun findLabel(v: View, text: String): TextView? {
        if (v is TextView && v.text.toString() == text) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findLabel(v.getChildAt(i), text)?.let { return it }
        return null
    }

    private fun awaitRow(text: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (onMain { visibleList()?.let { findLabel(it, text) } } != null) return
            SystemClock.sleep(50)
        }
        fail("Timed out waiting for a visible row labelled '$text'")
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Any? = null
        var error: Throwable? = null
        instrumentation.runOnMainSync {
            try { result = block() } catch (t: Throwable) { error = t }
        }
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
