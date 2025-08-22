package uz.shukrullaev.com.talksy

import jakarta.persistence.EntityNotFoundException
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant


/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 18/08/2025 6:09 pm
 */

fun getTelegramId(): String =
    (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)
        ?.token
        ?.claims
        ?.get("telegramId")
        ?.toString()
        ?: throw IllegalStateException("TelegramId not found in security context")


interface UserService {

    /**
     * Username bo‘yicha foydalanuvchini topadi.
     * va User malumot qaytaradi ichida rasmi ham boladi.
     */
    fun findByUsername(username: String): UserResponseDTO?

    /**
     * Foydalanuvchi profilini yangilash.
     * Parametrlar null bo‘lsa, o‘sha maydon o‘zgarmaydi.
     * qulay tarafi null bop qogan taqdirda bazaga null saqlanib qolishini oldini oladi
     */
    fun updateProfile(userId: Long, request: UserRequestDTO): UserResponseDTO

    /**
     * Telegram orqali login
     * Foydalanuvchi mavjud bo‘lsa, malumotlaga teymidi,
     * mavjud bo‘lmasa yangi foydalanuvchi yaratadi. va saqlab qo'yadi
     */
    fun saveOrUpdateFromTelegram(
        userRequestDTO: UserRequestDTO
    ): TokenDTO

}

interface ChatService {
    fun createChat(request: ChatRequestDTO): ChatResponseDTO
    fun getUserChats(username: String): List<ChatResponseDTO>
}

interface MessageService {
    fun sendMessage(request: MessageRequestDTO): MessageResponseDTO
    fun getMessages(username: String): List<MessageResponseDTO>
}


@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val hashUtils: HashUtils
) : UserService {

    override fun findByUsername(username: String): UserResponseDTO? =
        userRepository.findByUsernameAndDeletedFalse(username)?.toDTO()

    @Transactional
    override fun updateProfile(userId: Long, request: UserRequestDTO): UserResponseDTO {
        val user = userRepository.findByIdAndDeletedFalse(userId)
            ?: throw EntityNotFoundException("User not found")

        request.username?.let { user.username = it }
        request.firstName.let { user.firstName = it }
        request.lastName?.let { user.lastName = it }

        return userRepository.save(user).toDTO()
    }

    @Transactional
    override fun saveOrUpdateFromTelegram(
        userRequestDTO: UserRequestDTO
    ): TokenDTO {
        hashUtils.verifyTelegramHash(userRequestDTO)
        val user = userRepository.findByTelegramIdAndDeletedFalse(userRequestDTO.telegramId)
            ?: userRequestDTO.toEntity()
        return jwtService.generateToken(userRepository.save(user))
    }

}

@Service
class ChatServiceImpl(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val chatUserRepository: ChatUserRepository
) : ChatService {

    @Transactional
    override fun createChat(request: ChatRequestDTO): ChatResponseDTO {
        val telegramId = getTelegramId()
        val owner = userRepository.findByTelegramIdAndDeletedFalse(telegramId)
            ?: throw UserNotFound()

        val chat = chatRepository.save(request.toEntity())

        val chatUser = ChatUser(
            joinedDate = Instant.now(),
            isOwner = true,
            chat = chat,
            user = owner
        )
        chatUserRepository.save(chatUser)

        return chat.toDTO()
    }

    override fun getUserChats(username: String): List<ChatResponseDTO> {
        val user = userRepository.findByUsernameAndDeletedFalse(username)
            ?: throw UserNotFound()
        val chats = chatUserRepository.findAllByUserIdAndDeletedFalse(user.id!!)
            .map { it.chat }
        return chats.map { it.toDTO() }
    }
}

@Service
class MessageServiceImpl(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val messagingTemplate: SimpMessagingTemplate
) : MessageService {

    @Transactional
    override fun sendMessage(request: MessageRequestDTO): MessageResponseDTO {
        val chat = chatRepository.findById(request.chatId)
            .orElseThrow { ChatNotFound(request.chatId) }

        val sender = userRepository.findById(request.senderId)
            .orElseThrow { SenderNotFound(request.senderId) }

        val replyTo = request.replyToId?.let {
            messageRepository.findById(it).orElse(null)
        }

        val message = messageRepository.save(request.toEntity(chat, sender, replyTo))

        messagingTemplate.convertAndSend(
            "/topic/chat/${chat.id}",
            message.toDTO()
        )

        return message.toDTO()
    }

    override fun getMessages(username: String): List<MessageResponseDTO> {
        val user = userRepository.findByUsernameAndDeletedFalse(username)
            ?: throw UserNotFound()
        return messageRepository.findAllBySenderIdAndDeletedFalse(user.id!!)
            .map { it.toDTO() }
    }
}
