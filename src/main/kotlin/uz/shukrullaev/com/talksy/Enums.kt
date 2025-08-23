package uz.shukrullaev.com.talksy


/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 21/08/2025 6:23 pm
 */

enum class ExceptionsCode(val code: Int) {

    TELEGRAM_NOT_VALID(100),
    USER_NOT_FOUND(101),
    CHAT_NOT_FOUND(102),
    SENDER_NOT_FOUND(103),
    NOT_CHAT_MEMBER(104),
    MESSAGE_NOT_FOUND(105)


}

enum class Status {
    NOT_SENT,
    SENDING,
    SENT,
    DELIVERED,
    READ
}