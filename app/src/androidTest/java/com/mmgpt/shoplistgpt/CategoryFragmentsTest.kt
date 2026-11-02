package com.mmgpt.shoplistgpt

import android.content.Context
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Characterization tests for the per-category pages (CategoryFragment, PlanCategoryFragment)
 * and for view lifecycle, so the fragment base-class refactor can be checked for regressions.
 *
 * Seeded data: Alpha = [Milk, Eggs (checked), Bread], Bravo = [Rice].
 */
@RunWith(AndroidJUnit4::class)
class CategoryFragmentsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefs get() =
        instrumentation.targetContext.getSharedPreferences("state", Context.MODE_PRIVATE)

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity

    @Before
    fun seed() {
        val alpha = category(0, "Alpha", item(0, "Milk"), item(1, "Eggs", checked = true), item(2, "Bread"))
        val bravo = category(1, "Bravo", item(3, "Rice"))
        val state = JSONObject().put("categories", JSONArray().put(alpha).put(bravo))
        prefs.edit().clear().putString("state", state.toString()).commit()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        prefs.edit().clear().commit()
    }

    // ---------- Shop mode: CategoryFragment ----------

    @Test
    fun shop_page_showsHeadingAndCheckedCount() {
        launchShop()
        assertEquals("Alpha", text(R.id.tvHeading))
        assertEquals("1/3", text(R.id.tvCount))
        assertFalse(visible<View>(R.id.chkSelectAll).isShown)
        assertFalse(visible<View>(R.id.btnClearAll).isShown)
    }

    @Test
    fun shop_secondPage_showsItsOwnCategory() {
        launchShop()
        showPage(1)
        assertEquals("Bravo", text(R.id.tvHeading))
        assertEquals("0/1", text(R.id.tvCount))
    }

    @Test
    fun shop_checkingItem_updatesCountAndPersists() {
        launchShop()
        onMain { rowView("Milk").findViewById<CheckBox>(R.id.chkPurchased).performClick() }
        instrumentation.waitForIdleSync()
        assertEquals("2/3", text(R.id.tvCount))
        assertTrue(savedItem("Milk").getBoolean("isChecked"))
    }

    @Test
    fun shop_flaggingItem_persistsFlag() {
        launchShop()
        onMain { rowView("Bread").findViewById<View>(R.id.btnFlag).performClick() }
        instrumentation.waitForIdleSync()
        assertTrue(savedItem("Bread").getBoolean("isFlagged"))
    }

    @Test
    fun shop_recreate_keepsPagesWorking() {
        launchShop()
        recreateActivity()
        onMain { activity.findViewById<View>(R.id.btnShop).performClick() }
        showPage(0)
        assertEquals("Alpha", text(R.id.tvHeading))
        assertEquals("1/3", text(R.id.tvCount))
    }

    // ---------- Plan mode: PlanCategoryFragment ----------

    @Test
    fun plan_page_showsHeadingSelectAllAndItems() {
        launchPlan()
        showPage(1)
        assertEquals("Alpha", text(R.id.tvHeading))
        assertEquals("", text(R.id.tvCount))
        assertTrue(visible<View>(R.id.chkSelectAll).isShown)
        awaitRow("Milk")
        awaitRow("Bread")
    }

    @Test
    fun plan_secondPage_usesItsOwnIndex() {
        launchPlan()
        showPage(2)
        assertEquals("Bravo", text(R.id.tvHeading))
        awaitRow("Rice")
    }

    @Test
    fun plan_selectAll_checksEveryItem() {
        launchPlan()
        showPage(1)
        assertFalse(visible<CheckBox>(R.id.chkSelectAll).isChecked)
        onMain { visible<CheckBox>(R.id.chkSelectAll).performClick() }
        instrumentation.waitForIdleSync()
        listOf("Milk", "Eggs", "Bread").forEach { assertTrue(it, savedItem(it).getBoolean("isChecked")) }
        assertTrue(onMain { visible<CheckBox>(R.id.chkSelectAll).isChecked })
        assertFalse(savedItem("Rice").getBoolean("isChecked"))
    }

    @Test
    fun plan_categoriesPage_showsHeadingAndClearAll() {
        launchPlan()
        assertEquals("", text(R.id.tvCount))
        assertTrue(visible<View>(R.id.btnClearAll).isShown)
        assertFalse(visible<View>(R.id.chkSelectAll).isShown)
        awaitRow("Alpha")
        awaitRow("Bravo")
    }

    @Test
    fun plan_recreate_doesNotCrash_andRestoredPlanFragmentStillRenders() {
        launchPlan()
        showPage(1)
        recreateActivity()
        onMain { activity.findViewById<View>(R.id.btnShop).performClick() }
        showPage(0)
        // Current behaviour: the restored plan fragment is reused at page 0 even though the adapter is rebuilt in shop mode.
        assertEquals(activity.getString(R.string.categories_heading), text(R.id.tvHeading))
    }

    // ---------- Setup ----------

    private fun category(id: Long, heading: String, vararg items: JSONObject) =
        JSONObject().put("id", id).put("heading", heading)
            .put("items", JSONArray().also { arr -> items.forEach { arr.put(it) } })

    private fun item(id: Long, name: String, checked: Boolean = false) =
        JSONObject().put("id", id).put("name", name).put("isChecked", checked)

    private fun launchShop() {
        launchActivity()
        onMain { activity.findViewById<View>(R.id.btnShop).performClick() }
        showPage(0)
    }

    private fun launchPlan() {
        launchActivity()
        onMain { activity.findViewById<View>(R.id.btnPlan).performClick() }
        showPage(0)
    }

    private fun launchActivity() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity = it }
    }

    private fun recreateActivity() {
        scenario.recreate()
        scenario.onActivity { activity = it }
        instrumentation.waitForIdleSync()
    }

    private fun showPage(page: Int) {
        awaitPagerSettled()
        onMain { pager().setCurrentItem(page, false) }
        awaitPagerSettled()
        awaitVisible(R.id.tvHeading)
    }

    private fun awaitPagerSettled() {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (onMain { pager().scrollState } != ViewPager2.SCROLL_STATE_IDLE) {
            if (SystemClock.uptimeMillis() > deadline) fail("Pager never settled")
            SystemClock.sleep(50)
        }
    }

    // ---------- View lookup ----------

    private fun pager(): ViewPager2 = activity.findViewById(R.id.viewPager)

    /** The view with [id] on the page currently on screen (pages are at least half the screen wide). */
    private fun <T : View> visibleOrNull(id: Int): T? {
        var found: View? = null
        fun walk(v: View) {
            if (found != null) return
            if (v.id == id) {
                val r = Rect()
                val onScreen = v.getGlobalVisibleRect(r) || !v.isShown
                if (onScreen && inCurrentPage(v)) found = v
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(activity.window.decorView)
        @Suppress("UNCHECKED_CAST")
        return found as T?
    }

    private fun inCurrentPage(v: View): Boolean {
        var p: View? = v
        while (p != null && p.parent !== pager().getChildAt(0)) p = p.parent as? View
        val pageRoot = p ?: return false
        val r = Rect()
        return pageRoot.getGlobalVisibleRect(r) && r.width() > pageRoot.resources.displayMetrics.widthPixels / 2
    }

    private fun <T : View> visible(id: Int): T =
        checkNotNull(onMain { visibleOrNull<T>(id) }) { "No view with id $id on the current page" }

    private fun text(id: Int): String = onMain { visible<TextView>(id).text.toString() }

    private fun awaitVisible(id: Int) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (onMain { visibleOrNull<View>(id) } != null) return
            SystemClock.sleep(50)
        }
        fail("Timed out waiting for view $id")
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
            if (onMain { visibleOrNull<View>(R.id.recyclerView)?.let { findLabel(it, text) } } != null) return
            SystemClock.sleep(50)
        }
        fail("Timed out waiting for a visible row labelled '$text'")
    }

    /** The item card (parent of the name label) for the row labelled [name]. */
    private fun rowView(name: String): View {
        val list = checkNotNull(visibleOrNull<View>(R.id.recyclerView)) { "No list on the current page" }
        val label = checkNotNull(findLabel(list, name)) { "No row labelled '$name'" }
        return label.parent.parent as View
    }

    // ---------- Persisted state ----------

    private fun savedItem(name: String): JSONObject {
        val cats = JSONObject(prefs.getString("state", null)!!).getJSONArray("categories")
        for (i in 0 until cats.length()) {
            val items = cats.getJSONObject(i).getJSONArray("items")
            for (j in 0 until items.length()) {
                if (items.getJSONObject(j).getString("name") == name) return items.getJSONObject(j)
            }
        }
        throw AssertionError("No saved item named '$name'")
    }

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
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
