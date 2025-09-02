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
    MESSAGE_NOT_FOUND(105),
    ID_ISNULL(106),
    CHAT_CONFLICT(107),
    USER_SEND_MESSAGE_CONFLICT(109),
    BEFORE_ID_IS_DELETED(109),
    USERNAME_ALREADY_EXISTS(110),
    FILE_NOT_FOUND(111),
    CONFLICT_MESSAGE(112),
    TITLE_NULL(113),
    IDS_ISNULL(114),
    IS_NOT_OWNER(115),
}

enum class Status {
    NOT_SENT,
    SENDING,
    SENT,
    DELIVERED,
    READ,
    DELETED
}

enum class MessageType {
    TEXT,
    VIDEO,
    AUDIO,
    MP3,
    PHOTO,
    UNKNOWN
}
