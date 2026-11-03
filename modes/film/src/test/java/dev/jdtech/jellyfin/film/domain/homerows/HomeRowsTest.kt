package dev.jdtech.jellyfin.film.domain.homerows

import android.content.SharedPreferences
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowType.CONTINUE_WATCHING
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowType.FAVORITES
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowType.LATEST
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowType.NEXT_UP
import dev.jdtech.jellyfin.film.domain.homerows.HomeRowType.SUGGESTIONS
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRowsTest {
    private val movies = UUID.randomUUID()
    private val shows = UUID.randomUUID()
    private val legacy = LegacyHomeSwitches()

    private fun keys(rows: List<HomeRowConfig>) = rows.map { it.key }

    private fun visibleKeys(rows: List<HomeRowConfig>) = keys(rows.filter { it.visible })

    @Test
    fun `without a stored layout Home keeps its old order, with Favorites hidden at the end`() {
        val rows = HomeRows.resolve(null, listOf(movies, shows), legacy)

        assertEquals(
            listOf(
                "suggestions",
                "continue_watching",
                "next_up",
                "latest:$movies",
                "latest:$shows",
                "favorites",
            ),
            keys(rows),
        )
        assertEquals(keys(rows).dropLast(1), visibleKeys(rows))
    }

    @Test
    fun `the old switches carry over`() {
        val old = LegacyHomeSwitches(suggestions = false, continueWatching = true, nextUp = false, latest = false)

        val rows = HomeRows.resolve(null, listOf(movies), old)

        assertEquals(listOf("continue_watching"), visibleKeys(rows))
        assertEquals(false, HomeRows.needsLibraries(null, old))
        assertEquals(true, HomeRows.needsLibraries(null, legacy))
    }

    @Test
    fun `a stored layout wins over the old switches`() {
        val stored = HomeRows.defaults(legacy, listOf(movies))
        val old = LegacyHomeSwitches(false, false, false, false)

        val rows = HomeRows.resolve(stored, listOf(movies), old)

        assertEquals(visibleKeys(stored), visibleKeys(rows))
        assertEquals(true, HomeRows.needsLibraries(stored, old))
    }

    @Test
    fun `moving and hiding change only that row`() {
        var rows = HomeRows.defaults(legacy, listOf(movies))

        rows = HomeRows.move(rows, "favorites", -1)
        rows = HomeRows.setVisible(rows, "favorites", true)
        rows = HomeRows.setVisible(rows, "suggestions", false)
        rows = HomeRows.move(rows, "latest:$movies", -10)

        assertEquals(
            listOf("latest:$movies", "suggestions", "continue_watching", "next_up", "favorites"),
            keys(rows),
        )
        assertEquals(
            listOf("latest:$movies", "continue_watching", "next_up", "favorites"),
            visibleKeys(rows),
        )
        // The first row can't move up, the last can't move down.
        assertEquals(rows, HomeRows.move(rows, "latest:$movies", -1))
        assertEquals(rows, HomeRows.move(rows, "favorites", 1))
    }

    @Test
    fun `a new library is added at the end, visible, and a removed one is left out`() {
        val stored =
            listOf(
                HomeRowConfig(FAVORITES),
                HomeRowConfig(LATEST, movies, visible = false),
                HomeRowConfig(NEXT_UP),
                HomeRowConfig(SUGGESTIONS, visible = false),
                HomeRowConfig(CONTINUE_WATCHING),
            )
        val anime = UUID.randomUUID()

        val rows = HomeRows.resolve(stored, listOf(shows, anime), legacy)

        assertEquals(
            listOf(
                "favorites",
                "next_up",
                "suggestions",
                "continue_watching",
                "latest:$shows",
                "latest:$anime",
            ),
            keys(rows),
        )
        assertEquals(true, rows.last().visible)
        assertEquals(false, rows.first { it.type == SUGGESTIONS }.visible)
    }

    @Test
    fun `with the libraries unknown the stored Latest rows stay`() {
        val stored = listOf(HomeRowConfig(LATEST, movies, visible = false), HomeRowConfig(NEXT_UP))

        val rows = HomeRows.resolve(stored, null, legacy)

        assertEquals("latest:$movies", rows.first().key)
        assertEquals(false, rows.first().visible)
        // Row types missing from what's stored are added.
        assertEquals(
            setOf("latest:$movies", "next_up", "suggestions", "continue_watching", "favorites"),
            keys(rows).toSet(),
        )
    }

    @Test
    fun `encoding round-trips and skips what it can't read`() {
        val rows = HomeRows.setVisible(HomeRows.defaults(legacy, listOf(movies, shows)), "next_up", false)

        assertEquals(rows, HomeRows.decode(HomeRows.encode(rows)))
        assertNull(HomeRows.decode(null))
        assertEquals(
            listOf(HomeRowConfig(NEXT_UP, visible = false)),
            HomeRows.decode("next_up,,0;genres,,1;latest,,1;latest,not-a-uuid,1;broken"),
        )
    }

    @Test
    fun `each user on each server has their own layout`() = runBlocking {
        val prefs = AppPreferences(MemorySharedPreferences())
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()
        val servers =
            mutableMapOf(
                "server1" to Server("server1", "One", null, alice),
                "server2" to Server("server2", "Two", null, alice),
            )
        val store = HomeRowsStore(prefs, dao(servers))

        prefs.setValue(prefs.currentServer, "server1")
        val aliceOnOne = store.currentProfile()!!
        val layout = HomeRows.move(HomeRows.defaults(legacy, listOf(movies)), "favorites", -4)
        store.write(aliceOnOne, layout)

        assertEquals(layout, store.read(store.currentProfile()))

        // Bob on the same server, and Alice on another server, start from the defaults.
        servers["server1"] = Server("server1", "One", null, bob)
        assertNull(store.read(store.currentProfile()))
        prefs.setValue(prefs.currentServer, "server2")
        assertNull(store.read(store.currentProfile()))

        // Back to Alice on server 1.
        servers["server1"] = Server("server1", "One", null, alice)
        prefs.setValue(prefs.currentServer, "server1")
        assertEquals(layout, store.read(store.currentProfile()))
    }

    @Test
    fun `no server or user means no profile`() = runBlocking {
        val prefs = AppPreferences(MemorySharedPreferences())
        val store = HomeRowsStore(prefs, dao(mutableMapOf("s" to Server("s", "S", null, null))))

        assertNull(store.currentProfile())
        prefs.setValue(prefs.currentServer, "s")
        assertNull(store.currentProfile())
        assertNull(store.read(null))
    }

    @Test
    fun `the old switches are read from the preferences`() {
        val prefs = AppPreferences(MemorySharedPreferences())
        prefs.setValue(prefs.homeSuggestions, false)
        prefs.setValue(prefs.homeLatest, false)

        val old = HomeRowsStore(prefs, dao(mutableMapOf())).legacy()

        assertEquals(LegacyHomeSwitches(false, true, true, false), old)
    }

    private fun dao(servers: Map<String, Server>): ServerDatabaseDao =
        Proxy.newProxyInstance(
            ServerDatabaseDao::class.java.classLoader,
            arrayOf(ServerDatabaseDao::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getServer" -> servers[args!![0]]
                else -> throw NotImplementedError("ServerDatabaseDao.${method.name}")
            }
        } as ServerDatabaseDao
}

/** Just enough SharedPreferences for AppPreferences. */
private class MemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = values

    override fun getString(key: String, defValue: String?) =
        if (key in values) values[key] as String? else defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?) =
        if (key in values) values[key] as Set<String>? else defValues

    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue

    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue

    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue

    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue

    override fun contains(key: String) = key in values

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }

        override fun putStringSet(key: String, values: Set<String>?) =
            apply { this@MemorySharedPreferences.values[key] = values }

        override fun putInt(key: String, value: Int) = apply { values[key] = value }

        override fun putLong(key: String, value: Long) = apply { values[key] = value }

        override fun putFloat(key: String, value: Float) = apply { values[key] = value }

        override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }

        override fun remove(key: String) = apply { values.remove(key) }

        override fun clear() = apply { values.clear() }

        override fun commit() = true

        override fun apply() = Unit
    }
}
