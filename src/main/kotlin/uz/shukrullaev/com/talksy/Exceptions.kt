package uz.shukrullaev.com.talksy

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.util.*


/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 18/08/2025 6:08 pm
 */

sealed class ExceptionUtil(message: String? = null) : RuntimeException(message) {
    abstract fun exceptionType(): ExceptionsCode
    protected open fun getErrorMessageArguments(): Array<Any?>? = null
    fun getErrorMessage(errorMessageSource: ResourceBundleMessageSource): BaseMessage {
        return BaseMessage(
            exceptionType().code, errorMessageSource.getMessage(
                exceptionType().toString(), getErrorMessageArguments(), Locale(LocaleContextHolder.getLocale().language)
            )
        )
    }
}

@ControllerAdvice
class GlobalExceptionHandler(
    private val errorMessageSource: ResourceBundleMessageSource,
) {

    @ExceptionHandler(ExceptionUtil::class)
    fun handleAppExceptions(ex: ExceptionUtil): ResponseEntity<Any?> {
        return when (ex) {
            is ExceptionUtil -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource))

            else -> ResponseEntity.badRequest()
                .body(ex.getErrorMessage(errorMessageSource))
        }
    }
}


class TelegramDataIsNotValid(private val requestDTO: UserRequestDTO? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.TELEGRAM_NOT_VALID
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(requestDTO)
}


class TelegramDataIsNotValidException(private val requestDTO: String? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.TELEGRAM_NOT_VALID
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(requestDTO)
}


class UserNotFoundException(val username: String? = null) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.USER_NOT_FOUND
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(username)

}


class ChatConflictException(private val chatId: Long) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.CHAT_CONFLICT
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(chatId)

}

class UserSendMessageConflictException(private val userId: Long) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.USER_SEND_MESSAGE_CONFLICT
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(userId)

}

class ObjectIdNullException() : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.ID_ISNULL
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf()
}

class ChatIsNotGroupException(val isGroup: Boolean) : ExceptionUtil() {
    override fun exceptionType(): ExceptionsCode = ExceptionsCode.ID_ISNULL

    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(isGroup)


}


class ChatNotFoundException(private val chatId: Long) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.CHAT_NOT_FOUND
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(chatId)
}

class SenderNotFoundException(private val senderId: Long) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.SENDER_NOT_FOUND
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(senderId)
}

class NotChatMemberException : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.NOT_CHAT_MEMBER
}

class MessageNotFoundException(private val messageId: Long) : ExceptionUtil() {
    override fun exceptionType() = ExceptionsCode.MESSAGE_NOT_FOUND
    override fun getErrorMessageArguments(): Array<Any?> = arrayOf(messageId)
}