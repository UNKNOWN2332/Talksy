package uz.shukrullaev.com.talksy

import jakarta.validation.constraints.NotBlank
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
    @field:NotNull val isGroup: Boolean
)

data class ChatResponseDTO(
    val id: Long,
    val title: String?,
    val isGroup: Boolean,
    val createdDate: Instant?
)

fun ChatRequestDTO.toEntity() = Chat(
    title = title,
    isGroup = isGroup
)

fun Chat.toDTO() = ChatResponseDTO(
    id = id!!,
    title = title,
    isGroup = isGroup,
    createdDate = createdDate
)


data class MessageRequestDTO(
    val content: String?,
    @field:NotNull val chatId: Long,
    @field:NotNull val senderId: Long,
    val replyToId: Long? = null
)

data class MessageResponseDTO(
    val id: Long,
    val content: String?,
    val createdDate: Instant?,
    val chatId: Long,
    val senderId: Long,
    val replyToId: Long?,
    val attachments: List<AttachmentResponseDTO> = emptyList()
)

fun MessageRequestDTO.toEntity(chat: Chat, sender: User, replyTo: Message? = null) = Message(
    content = content,
    chat = chat,
    sender = sender,
    replyTo = replyTo
)

fun Message.toDTO() = MessageResponseDTO(
    id = id!!,
    content = content,
    createdDate = createdDate,
    chatId = chat.id!!,
    senderId = sender.id!!,
    replyToId = replyTo?.id,
    attachments = emptyList()
)


data class ChatUserRequestDTO(
    @field:NotNull val joinedDate: Instant,
    @field:NotNull val isOwner: Boolean,
    val chatId: Long,
    val userId: Long
)

data class ChatUserResponseDTO(
    val id: Long,
    val joinedDate: Instant,
    val isOwner: Boolean,
    val chatId: Long,
    val userId: Long
)

fun ChatUserRequestDTO.toEntity(chat: Chat, user: User) = ChatUser(
    joinedDate = joinedDate,
    isOwner = isOwner,
    chat = chat,
    user = user
)

fun ChatUser.toDTO() = ChatUserResponseDTO(
    id = id!!,
    joinedDate = joinedDate,
    isOwner = isOwner,
    chatId = chat.id!!,
    userId = user.id!!
)


data class AttachmentRequestDTO(
    @field:NotBlank val url: String,
    @field:NotBlank val type: String,
    val size: Long?,
    val duration: Int?,
    @field:NotBlank val fileHash: String,
    val messageId: Long
)

data class AttachmentResponseDTO(
    val id: Long,
    val url: String,
    val type: String,
    val size: Long?,
    val duration: Int?,
    val messageId: Long
)

fun AttachmentRequestDTO.toEntity(message: Message) = Attachment(
    url = url,
    type = type,
    size = size,
    duration = duration,
    fileHash = fileHash,
    message = message
)

fun Attachment.toDTO() = AttachmentResponseDTO(
    id = id!!,
    url = url,
    type = type,
    size = size,
    duration = duration,
    messageId = message.id!!
)
