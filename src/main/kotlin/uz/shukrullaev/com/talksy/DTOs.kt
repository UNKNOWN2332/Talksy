package uz.shukrullaev.com.talksy

import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.*

/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 18/08/2025 6:09 pm
 */

data class BaseMessage(val code: Int, val message: String?)

data class TokenDTO(
    val token: String?,
    val tokenCreateAt: Date?,
    val expiredTime: Date?,
)

data class UserRequestDTO(
    @field:NotNull val telegramId: String,
    val username: String?,
    val firstName: String,
    val lastName: String?,
    val photoUrl: String?,
    val authDate: Instant,
    val hash: String
)

data class UserResponseDTO(
    val id: Long,
    val telegramId: String,
    val username: String?,
    val firstName: String,
    val lastName: String?,
    val photoUrl: String?,
    val authDate: Instant?
)

fun UserRequestDTO.toEntity() = User(
    telegramId = telegramId,
    username = username,
    firstName = firstName,
    lastName = lastName,
    photoUrl = photoUrl,
    authDate = authDate
)

fun User.toDTO() = UserResponseDTO(
    id = id!!,
    telegramId = telegramId,
    username = username,
    firstName = firstName,
    lastName = lastName,
    photoUrl = photoUrl,
    authDate = authDate
)

data class ChatRequestDTO(
    val title: String?,
    @field:NotNull var isGroup: Boolean
)

data class ChatResponseDTO(
    val id: Long,
    val title: String?,
    val isGroup: Boolean,
    val createdDate: Instant?
)

fun ChatRequestDTO.toEntity() = Chat(title = title, isGroup = isGroup)

fun Chat.toDTO() = ChatResponseDTO(
    id = id!!,
    title = title,
    isGroup = isGroup,
    createdDate = createdDate
)

data class MessageRequestDTO(
    val fromTelegramId: String,
    val toTelegramId: String? = null,
    val chatId: Long? = null,
    val isGroup: Boolean = false,
    val content: String? = null,
    val caption: String? = null,
    val replyToId: Long? = null
)

data class MessageStatusResponseDTO(
    val id: Long,
    val messageId: Long,
    val userId: Long,
    val status: Status,
    val createdDate: Instant?
)

fun MessageStatus.toDTO() = MessageStatusResponseDTO(
    id = id!!,
    messageId = message.id!!,
    userId = user.id!!,
    status = status,
    createdDate = createdDate
)

data class MessageResponseDTO(
    val id: Long,
    val chatId: Long,
    val fromTelegramId: String,
    val content: String?,
    val caption: String?,
    val attachments: List<AttachmentInfo> = emptyList(),
    val createdDate: Instant,
    val customHash: String? = null,
    val status: Status
)

data class AttachmentInfo(
    val id: Long?,
    val customHash: String,
    val type: String?,
    val size: Long?,
    val duration: Int?,
    val height: Int?,
    val width: Int?
)

fun MessageRequestDTO.toEntity(chat: Chat, sender: User, replyTo: Message? = null) =
    Message(content = content, chat = chat, sender = sender, caption = caption, replyTo = replyTo)

fun Message.toDTO(status: Status): MessageResponseDTO = MessageResponseDTO(
    id = id!!,
    chatId = chat.id!!,
    fromTelegramId = sender.telegramId,
    content = content,
    caption = caption,
    attachments = attachments.map { it.toInfo() },
    createdDate = createdDate!!,
    status = status
)

fun Attachment.toInfo() = AttachmentInfo(
    id = id!!,
    customHash = file.customHash,
    type = file.mimeType,
    size = file.size,
    duration = file.duration,
    height = file.height,
    width = file.width
)

data class ChatUserRequestDTO(
    val chatId: Long,
    val userIds: List<Long>
)

data class ChatUserResponseDTO(
    val id: Long,
    val joinedDate: Instant,
    val isOwner: Boolean,
    val chatId: Long,
    val userId: Long
)

fun ChatUserRequestDTO.toEntity(chat: Chat, user: User) =
    ChatUser(joinedDate = Instant.now(), isOwner = false, chat = chat, user = user)

fun ChatUser.toDTO() = ChatUserResponseDTO(
    id = id!!,
    joinedDate = joinedDate,
    isOwner = isOwner,
    chatId = chat.id!!,
    userId = user.id!!
)

data class ChatRequestDtoForUsers(val username: String)

data class ChatResponseDtoForUsers(
    val id: Long,
    val participants: List<String>,
    val isGroup: Boolean? = false,
    val createdDate: Instant,
    var name: String? = null
)

interface ChatsWithNew {
    fun getChatId(): Long
    fun getTitle(): String?
    fun getLastMessageTime(): Instant?
    fun getNewMessageNumber(): Long
}

data class ChatMessagesRequestDto(
    val chatId: Long,
    val beforeId: Long?,
    val limit: Int = 20
)

data class ChatMessagesResponseDto(
    val messages: List<MessageResponseDTO>,
    val nextBeforeId: Long?,
    val hasMore: Boolean
)

data class MessageUpdateRequestDTO(
    val chatId: Long,
    val messageId: Long,
    val content: String?,
    val caption: String?,
    val customHash: String,
)

