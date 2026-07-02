package com.guardian.app.walls.wall2.event

sealed class Decision {
    data object Block : Decision()
    data class Warn(val message: String) : Decision()
    data object Ignore : Decision()
    data object Log : Decision()
    data object Observe : Decision()
    data class Recover(val errorType: ErrorType) : Decision()
}

sealed class ErrorType {
    data object Recoverable : ErrorType()
    data object Fatal : ErrorType()
    data object Permission : ErrorType()
    data object Network : ErrorType()
    data object Unexpected : ErrorType()
}
