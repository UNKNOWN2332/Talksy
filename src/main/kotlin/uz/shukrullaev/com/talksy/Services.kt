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
        ?: throw TelegramDataIsNotValid()


interface UserService {

    /**
     * Username bo‘yicha foydalanuvchini topadi.
     * va User malumot qaytaradi ichida rasmi ham boladi.
     */
    fun findByUsername(username: String): UserResponseDTO?

    fun searchUsers(keyword: String): List<UserResponseDTO>

    /**
     * Foydalanuvchi profilini yangilash.
     * Parametrlar null bo‘lsa, o‘sha maydon o‘zgarmaydi.
     * qulay tarafi null bop qogan taqdirda bazaga null saqlanib qolishini oldini oladi
     */
    fun updateProfile(request: UserRequestDTO): UserResponseDTO

    /**
     * Telegram orqali login
     * Foydalanuvchi mavjud bo‘lsa, malumotlaga teymidi,
     * mavjud bo‘lmasa yangi foydalanuvchi yaratadi. va saqlab qo'yadi
     */
    fun saveOrUpdateFromTelegram(
        userRequestDTO: UserRequestDTO
    ): TokenDTO

    fun me(): UserResponseDTO

}

interface ChatService {
    fun createChat(request: ChatRequestDTO): ChatResponseDTO
    fun getOrCreateDirectChat(request: ChatRequestDtoForUsers): ChatResponseDtoForUsers
    fun getMyChats(): List<ChatResponseDtoForUsers>
}

interface MessageService {
    fun sendMessage(request: MessageRequestDTO): MessageResponseDto
    fun getChatMessages(chatId: Long): List<MessageResponseDto>
}


@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val hashUtils: HashUtils
) : UserService {

    override fun findByUsername(username: String): UserResponseDTO? =
        userRepository.findByUsernameAndDeletedFalse(username)?.toDTO()

    override fun searchUsers(keyword: String): List<UserResponseDTO> =
        userRepository.searchByUsername(keyword).map { it.toDTO() }

    @Transactional
    override fun updateProfile(request: UserRequestDTO): UserResponseDTO {
        val user = userRepository.findByTelegramIdAndDeletedFalse(getTelegramId())
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

    override fun me(): UserResponseDTO {
        return userRepository.findByTelegramIdAndDeletedFalse(getTelegramId())
            ?.toDTO()
            ?: throw UserNotFoundException()
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
            ?: throw UserNotFoundException()

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

    @Transactional
    override fun getOrCreateDirectChat(request: ChatRequestDtoForUsers): ChatResponseDtoForUsers {
        val telegramId = getTelegramId()
        val owner = userRepository.findByTelegramIdAndDeletedFalse(telegramId)
            ?: throw UserNotFoundException()

        val target = userRepository.findByUsernameAndDeletedFalse(request.username)
            ?: throw UserNotFoundException()

        if (owner.id == target.id) throw IllegalArgumentException("Cannot create chat with yourself")


        var chat = chatRepository.findDirectChat(owner.id!!, target.id!!)
            ?: chatRepository.findDirectChat(target.id!!, owner.id!!)

        if (chat == null) {

            chat = chatRepository.save(Chat(title = null, isGroup = false))

            listOf(
                owner to true,
                target to false
            ).forEach { (user, isOwner) ->
                chatUserRepository.save(
                    ChatUser(
                        chat = chat,
                        user = user,
                        isOwner = isOwner,
                        joinedDate = Instant.now()
                    )
                )
            }
        }

        val participants = chatUserRepository.findUsersByChatId(chat.id!!).mapNotNull { it.username }

        return ChatResponseDtoForUsers(
            id = chat.id!!,
            participants = participants,
            createdDate = chat.createdDate ?: Instant.now()
        )
    }

    override fun getMyChats(): List<ChatResponseDtoForUsers> {
        val telegramId = getTelegramId()
        val user = userRepository.findByTelegramIdAndDeletedFalse(telegramId)
            ?: throw UserNotFoundException()

        val chatUsers = chatUserRepository.findAllByUserIdAndDeletedFalse(user.id!!)

        return chatUsers.map { chatUser ->
            val chat = chatUser.chat
            val participants = chatUserRepository.findUsersByChatId(chat.id!!).mapNotNull { it.username }
            ChatResponseDtoForUsers(
                id = chat.id!!,
                participants = participants,
                createdDate = chat.createdDate!!
            )
        }
    }
}

@Service
class MessageServiceImpl(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val messageStatusRepository: MessageStatusRepository,
    private val chatUserRepository: ChatUserRepository
) : MessageService {

    @Transactional
    override fun sendMessage(request: MessageRequestDTO): MessageResponseDto {
        val chat = chatRepository.findById(request.chatId)
            .orElseThrow { ChatNotFoundException(request.chatId) }

        val telegramId = getTelegramId()
        val sender = userRepository.findByTelegramIdAndDeletedFalse(telegramId)
            ?: throw UserNotFoundException()

        if (!chatUserRepository.existsByChatIdAndUserIdAndDeletedFalse(request.chatId, sender.id!!)) {
            throw NotChatMemberException()
        }

        val replyTo = request.replyToId?.let {
            messageRepository.findById(it).orElseThrow { MessageNotFoundException(it) }
        }

        val message = messageRepository.save(request.toEntity(chat, sender, replyTo))

        val chatUsers = chatUserRepository.findAllByChatIdAndDeletedFalse(chat.id!!)
        chatUsers.forEach { cu ->
            val status = if (cu.user.id == sender.id) Status.READ else Status.SENT
            messageStatusRepository.save(MessageStatus(message = message, user = cu.user, status = status))
        }

        val broadcastDto = message.toResponseDto(Status.SENT)
        messagingTemplate.convertAndSend(
            "/topic/chat/${chat.id}",
            broadcastDto
        )

        return broadcastDto.copy(status = Status.READ)
    }

    override fun getChatMessages(chatId: Long): List<MessageResponseDto> {
        chatRepository.findById(chatId).orElseThrow { ChatNotFoundException(chatId) }

        val telegramId = getTelegramId()
        val currentUser = userRepository.findByTelegramIdAndDeletedFalse(telegramId)
            ?: throw UserNotFoundException()

        if (!chatUserRepository.existsByChatIdAndUserIdAndDeletedFalse(chatId, currentUser.id!!)) {
            throw NotChatMemberException()
        }

        val messages = messageRepository.findAllByChatIdAndDeletedFalse(chatId)
        val statusList = messageStatusRepository.findAllByUserIdAndMessageChatId(currentUser.id!!, chatId)
        val statusMap = statusList.associateBy { it.message.id!! }

        return messages.map { msg ->
            val status = statusMap[msg.id]?.status ?: Status.NOT_SENT
            msg.toResponseDto(status)
        }
    }
}