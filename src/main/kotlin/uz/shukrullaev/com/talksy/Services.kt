package uz.shukrullaev.com.talksy

import jakarta.persistence.EntityNotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 18/08/2025 6:09 pm
 */

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
    fun getUserChats(userId: Long): List<ChatResponseDTO>
}

interface MessageService {
    fun sendMessage(request: MessageRequestDTO): MessageResponseDTO
    fun getMessages(chatId: Long): List<MessageResponseDTO>
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
//
//@Service
//class ChatServiceImpl(
//    private val chatRepository: ChatRepository,
//    private val userRepository: UserRepository,
//    private val chatUserRepository: ChatUserRepository
//) : ChatService {
//
//    @Transactional
//    override fun createChat(request: ChatRequestDTO): ChatResponseDTO {
//
//        val owner = userRepository.findById(ownerId)
//            .orElseThrow { UserNotFound() }
//
//        val chat = chatRepository.save(request.toEntity())
//
//        val chatUser = ChatUser(
//            joinedDate = Instant.now(),
//            isOwner = true,
//            chat = chat,
//            user = owner
//        )
//        chatUserRepository.save(chatUser)
//
//        return chat.toDTO()
//    }
//
//    override fun getUserChats(userId: Long): List<ChatResponseDTO> {
//        val chats = chatUserRepository.findByUserId(userId).map { it.chat }
//        return chats.map { it.toDTO() }
//    }
//}
//
//
//@Service
//class MessageServiceImpl(
//    private val messageRepository: MessageRepository,
//    private val chatRepository: ChatRepository,
//    private val userRepository: UserRepository,
//    private val messagingTemplate: SimpMessagingTemplate
//) : MessageService {
//
//    @Transactional
//    override fun sendMessage(request: MessageRequestDTO): MessageResponseDTO {
//        val chat = chatRepository.findById(request.chatId)
//            .orElseThrow { ChatNotFound(request.chatId) }
//
//        val sender = userRepository.findById(request.senderId)
//            .orElseThrow { SenderNotFound(request.senderId) }
//
//        val replyTo = request.replyToId?.let {
//            messageRepository.findById(it).orElse(null)
//        }
//
//        val message = messageRepository.save(request.toEntity(chat, sender, replyTo))
//
//        messagingTemplate.convertAndSend(
//            "/topic/chat/${chat.id}",
//            message.toDTO()
//        )
//
//        return message.toDTO()
//    }
//
//    override fun getMessages(chatId: Long): List<MessageResponseDTO> {
//        return messageRepository.findbyTelegramId(chatId).map { it.toDTO() }
//    }
//}
