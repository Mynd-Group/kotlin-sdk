package core.utils

class UnixTimeFormatter {
    companion object {
        fun getCurrentUnixTimeInMS() : Long {
            return System.currentTimeMillis()
        }
    }
}