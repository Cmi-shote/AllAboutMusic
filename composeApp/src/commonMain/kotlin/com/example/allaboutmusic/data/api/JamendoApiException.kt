package com.example.allaboutmusic.data.api

class JamendoApiException(
    val code: Int,
    override val message: String
) : Exception(message)
