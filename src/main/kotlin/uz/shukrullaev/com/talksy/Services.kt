package uz.shukrullaev.com.talksy

import jakarta.persistence.EntityNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


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
    fun searchByUsername(username: String): List<UserResponseDTO>

    /**
     * Foydalanuvchi profilini yangilash.
     * Parametrlar null bo‘lsa, o‘sha maydon o‘zgarmaydi.
     * qulay tarafi null bop qogan taqdirda bazaga null saqlanib qolishini oldini oladi
     */
    fun updateProfile(request: UserRequestDTO): UserResponseDTO

    fun uploadImage(file: MultipartFile): String

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
    fun sendMessage(request: MessageRequestDTO, files: List<MultipartFile>? = null): MessageResponseDTO
    fun getChatMessages(chatId: Long): List<MessageResponseDTO>
}

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val hashUtils: HashUtils,
    private val fileUtils: FileUtils,
    private val userFileRepository: UserFileRepository,
    @Value("\${file.image.path}") private val uploadImageDir: String,
) : UserService {

    override fun searchByUsername(username: String): List<UserResponseDTO> =
        userRepository.searchByUsername(username).map { it.toDTO() }

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
    override fun uploadImage(file: MultipartFile): String {
        if (file.isEmpty) throw IllegalArgumentException("File is empty!")

        val hash = fileUtils.calculateSHA256(file.bytes)
        val user = getCurrentUser()

        userFileRepository.findBySha256Hash(hash)?.let { existing ->
            return linkExistingFile(user, existing)
        }

        val savedFile = createAndSaveFile(file, hash)

        return linkNewFile(user, savedFile, hash)
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

    private fun getCurrentUser(): User =
        userRepository.findByTelegramIdAndDeletedFalse(getTelegramId())
            ?: throw UserNotFoundException()

    private fun linkExistingFile(user: User, existing: UserFile): String {
        if (!userFileRepository.existsByOwnerIdAndSha256Hash(user.id!!, existing.sha256Hash)) {
            userFileRepository.save(
                UserFile(
                    ownerId = user.id!!,
                    filePath = existing.filePath,
                    sha256Hash = existing.sha256Hash,
                    customHash = existing.customHash
                )
            )
        }
        user.photoUrl = existing.filePath
        userRepository.save(user)
        return existing.customHash
    }

    private fun createAndSaveFile(file: MultipartFile, hash: String): File {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
        val extension = file.originalFilename?.substringAfterLast('.', "") ?: "jpg"
        val uniqueFileName = "$hash-$timestamp.$extension"

        File(uploadImageDir).apply { if (!exists()) mkdirs() }
        val path = Paths.get(uploadImageDir, uniqueFileName)
        Files.write(path, file.bytes)

        return path.toFile()
    }

    private fun linkNewFile(user: User, savedFile: File, hash: String): String {
        val fileNameHash = fileUtils.generateObfuscatedFileName(savedFile.name)

        user.photoUrl = savedFile.absolutePath
        userRepository.save(user)

        userFileRepository.save(
            UserFile(
                ownerId = user.id!!,
                filePath = savedFile.absolutePath,
                sha256Hash = hash,
                customHash = fileNameHash
            )
        )

        return fileNameHash
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
    private val chatUserRepository: ChatUserRepository,
    private val attachmentRepository: AttachmentRepository,
    private val userFileRepository: UserFileRepository,
    private val fileUtils: FileUtils,
    @Value("\${file.attachments.path}") private val attachmentsDir: String
) : MessageService {

    @Transactional
    override fun sendMessage(request: MessageRequestDTO, files: List<MultipartFile>?): MessageResponseDTO {
        val sender = userRepository.findByTelegramIdAndDeletedFalse(getTelegramId())
            ?: throw UserNotFoundException(getTelegramId())

        val chat = if (request.isGroup) {
            val cid = request.chatId ?: throw ObjectIdNullException()
            chatRepository.findById(cid).orElseThrow { ChatNotFoundException(cid) }
                .also { if (!it.isGroup) throw ChatIsNotGroupException(it.isGroup) }
        } else {
            val toTid = request.toTelegramId ?: throw ObjectIdNullException()
            val target = userRepository.findByTelegramIdAndDeletedFalse(toTid)
                ?: throw UserNotFoundException("recipient $toTid not found")
            findOrCreateDirectChat(sender, target)
        }

        if (!chatUserRepository.existsByChatIdAndUserIdAndDeletedFalse(chat.id!!, sender.id!!)) {
            throw NotChatMemberException()
        }

        val replyTo = request.replyToId?.let { rid ->
            messageRepository.findById(rid).orElseThrow { MessageNotFoundException(rid) }
                .also { if (it.chat.id != chat.id) throw IllegalArgumentException("replyTo message not in same chat") }
        }

        val message = Message(chat = chat, sender = sender, replyTo = replyTo, caption = request.caption, content = request.content)
        val savedMessage = messageRepository.save(message)

        val attachmentInfos = files?.map { saveAttachmentAndEntity(it, savedMessage) }
            ?.map { AttachmentInfo(it.id!!, it.url, it.type, it.size) } ?: emptyList()

        val chatUsers = chatUserRepository.findAllByChatIdAndDeletedFalse(chat.id!!)
        val statuses = chatUsers.map { cu ->
            val status = if (cu.user.id == sender.id) Status.READ else Status.SENT
            MessageStatus(message = savedMessage, user = cu.user, status = status)
        }
        messageStatusRepository.saveAll(statuses)

        val responseDto = savedMessage.toDTO(Status.SENT).copy(attachments = attachmentInfos)

        messagingTemplate.convertAndSend("/topic/chat/${chat.id}", responseDto)
        chatUsers.forEach { cu ->
            messagingTemplate.convertAndSendToUser(cu.user.telegramId, "/queue/messages", responseDto)
        }

        return responseDto.copy(status = Status.READ)
    }

    private fun findOrCreateDirectChat(sender: User, target: User): Chat {
        if (sender.id == target.id) throw IllegalArgumentException("Cannot send message to yourself")
        val existing = chatRepository.findDirectChat(sender.id!!, target.id!!) ?: chatRepository.findDirectChat(target.id!!, sender.id!!)
        if (existing != null) return existing

        val chat = chatRepository.save(Chat(title = null, isGroup = false))
        listOf(sender to true, target to false).forEach { (user, isOwner) ->
            chatUserRepository.save(ChatUser(joinedDate = Instant.now(), isOwner = isOwner, chat = chat, user = user))
        }
        return chat
    }

    private fun saveAttachmentAndEntity(file: MultipartFile, message: Message): Attachment {
        val bytes = file.bytes
        val sha = fileUtils.calculateSHA256(bytes)

        val existing = userFileRepository.findBySha256Hash(sha)
        val savedPath = existing?.filePath ?: run {
            File(attachmentsDir).apply { if (!exists()) mkdirs() }
            val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
            val ext = file.originalFilename?.substringAfterLast('.', "") ?: "bin"
            val uniqueName = "${sha.take(12)}-$timestamp.$ext"
            val path = Paths.get(attachmentsDir, uniqueName)
            Files.write(path, bytes)

            val obf = fileUtils.generateObfuscatedFileName(uniqueName)
            val uf = UserFile(ownerId = message.sender.id!!, filePath = path.toAbsolutePath().toString(), sha256Hash = sha, customHash = obf)
            userFileRepository.save(uf)
            uf.filePath
        }

        return attachmentRepository.save(Attachment(url = savedPath, type = file.contentType ?: "bin", size = file.size, fileHash = sha, message = message))
    }

    private fun getCurrentUserId(): Long {
        val tid = (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)
            ?.token?.claims?.get("telegramId")?.toString() ?: throw TelegramDataIsNotValid()
        return userRepository.findByTelegramIdAndDeletedFalse(tid)?.id ?: throw UserNotFoundException()
    }

    override fun getChatMessages(chatId: Long): List<MessageResponseDTO> {
        chatRepository.findById(chatId).orElseThrow { ChatNotFoundException(chatId) }
        val messages = messageRepository.findAllByChatIdAndDeletedFalse(chatId)
        val statusList = messageStatusRepository.findAllByUserIdAndMessageChatId(getCurrentUserId(), chatId)
        val statusMap = statusList.associateBy { it.message.id!! }
        return messages.map { m ->
            val st = statusMap[m.id]?.status ?: Status.SENT
            m.toDTO(st)
        }
    }
}
