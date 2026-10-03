package com.shilapi.xcertplay

/** Selective source baseline; dispositions and API23 gates are in docs/UPSTREAM_SYNC.md. */
internal object UpstreamSyncInfo {
    const val VERSION = "0.2.10"
    const val COMMIT = "3e43e25c55921bdf5149f5f92851acf202ed353a"
    const val LOCAL_VERSION = "0.2.10-h6"

    fun report() = "Upstream baseline v$VERSION commit=$COMMIT; selective H6/API23 port, local=$LOCAL_VERSION"
}
