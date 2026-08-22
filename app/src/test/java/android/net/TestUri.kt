package android.net

import android.os.Parcel

/** Minimal JVM-test Uri; Android's local stub throws from every static factory. */
object TestUri : Uri() {
    override fun buildUpon(): Builder = error("Not needed by this test")
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String = "/new-story"
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String = "//books/new-story"
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String = "books"
    override fun getLastPathSegment(): String = "new-story"
    override fun getPath(): String = "/new-story"
    override fun getPathSegments(): List<String> = listOf("new-story")
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String = "content"
    override fun getSchemeSpecificPart(): String = "//books/new-story"
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun toString(): String = "content://books/new-story"
    override fun describeContents(): Int = 0
    override fun writeToParcel(destination: Parcel, flags: Int) = Unit
}
