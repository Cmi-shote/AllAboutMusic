package com.example.allaboutmusic

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform