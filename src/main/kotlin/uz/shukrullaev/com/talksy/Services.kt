package uz.shukrullaev.com.talksy

import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 18/08/2025 6:09 pm
 */

fun getTelegramId(): String {
    val logger = LoggerFactory.getLogger("GetTelegramId")
    val authentication = SecurityContextHolder.getContext().authentication
        ?: run {
            logger.error("SecurityContextHolder authentication is null. Ensure the WebSocket STOMP CONNECT or HTTP request includes a valid 'Authorization: Bearer <token>' header.")
            throw TelegramDataIsNotValidException("Authentication is null. Provide a valid JWT token in the 'Authorization' header.")
        }

    logger.debug("Authentication type: ${authentication.javaClass.name}, Principal: ${authentication.principal}")

    if (authentication is JwtAuthenticationToken) {
        val jwt = authentication.token
        val telegramId = jwt.claims["telegramId"]?.toString()
        if (telegramId == null) {
            logger.error("telegramId claim is missing in JWT. Claims: ${jwt.claims}")
            throw TelegramDataIsNotValidException("telegramId claim is missing in JWT")
        }
        return telegramId
    }

    if (authentication is AnonymousAuthenticationToken) {
        logger.error("Anonymous authentication detected. Expected a valid JWT token in STOMP CONNECT or HTTP request.")
        throw TelegramDataIsNotValidException("Anonymous authentication is not allowed. Provide a valid JWT token in the 'Authorization' header.")
    }

    logger.error("Unsupported authentication type: ${authentication.javaClass.name}")
    throw TelegramDataIsNotValidException("Unsupported authentication type: ${authentication.javaClass.name}")
}

interface FileService {
    fun upload(file: MultipartFile, ownerId: Long, dir: String, messageType: MessageType = MessageType.TEXT): AppFile
    fun uploadFile(file: MultipartFile): AttachmentInfo
}

interface UserService {
    fun searchByUsername(username: String): UserResponseDTO?

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

    fun me(telegramId: String): UserResponseDTO

    fun getCurrentUser(): User

}

interface ChatService {
    fun createGroup(request: ChatRequestDTO): ChatResponseDTO
    fun addMemberToGroup(request: ChatUserRequestDTO)

    //    fun getOrCreateDirectChat(request: ChatRequestDtoForUsers): ChatResponseDtoForUsers
//    fun getMyChats(): List<ChatResponseDtoForUsers>

    fun getChats(pageable: Pageable): Page<ChatsWithNew>
}

interface MessageService {
    fun sendMessage(request: MessageRequestDTO, fileHash: String? = null)
    fun getChatMessages(request: ChatMessagesRequestDto)

    fun deleteMessages(request: ChatMessagesRequestDto, deleteMessageIds: List<Long>)

    fun updateMessage(request: MessageUpdateRequestDTO)
}

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val hashUtils: HashUtils,
    private val fileService: FileService,
    private val messagingTemplate: SimpMessagingTemplate,
    @Value("\${file.image.path}") private val uploadImageDir: String,
) : UserService {

    override fun searchByUsername(username: String): UserResponseDTO? =
        userRepository.searchByUsername(username)?.toDTO()
            ?.run { sendToUser(getTelegramId(), this); this }


    @Transactional
    override fun updateProfile(request: UserRequestDTO): UserResponseDTO {
        val user = userRepository.findByTelegramIdAndDeletedFalse(getTelegramId())
            ?: throw EntityNotFoundException("User not found")
        request.username?.let {
            userRepository.existsByUsernameAndDeletedFalse(it).runIfTrue { throw UserAlreadyExistsException(it) }
        }
        request.username?.let { user.username = it }
        request.firstName.let { user.firstName = it }
        request.lastName?.let { user.lastName = it }

        return userRepository.save(user).toDTO()
    }


    @Transactional
    override fun uploadImage(file: MultipartFile): String {
        val user = getCurrentUser()
        val appFile = fileService.upload(file, user.id!!, uploadImageDir, MessageType.PHOTO)
        user.photoUrl = appFile.customHash
        userRepository.save(user)
        sendToUser(user.telegramId, appFile.customHash)
        return appFile.customHash
    }

    @Transactional
    override fun saveOrUpdateFromTelegram(
        userRequestDTO: UserRequestDTO
    ): TokenDTO {
        hashUtils.verifyTelegramHash(userRequestDTO)
        val user = userRepository.findByTelegramIdAndDeletedFalse(userRequestDTO.telegramId)
            ?: userRequestDTO.toEntity()
        user.username = null
        return jwtService.generateToken(userRepository.save(user))
    }

    override fun me(telegramId: String): UserResponseDTO {
        return userRepository.findByTelegramIdAndDeletedFalse(telegramId)
            ?.toDTO()
            ?: throw UserNotFoundException()
    }

    fun sendToUser(telegramId: String, response: Any) {
        messagingTemplate.convertAndSendToUser(telegramId, "/queue/messages", response)
    }

    override fun getCurrentUser(): User =
        userRepository.findByTelegramIdAndDeletedFalse(getTelegramId())
            ?: throw UserNotFoundException()

}


@Service
class ChatServiceImpl(
    private val chatRepository: ChatRepository,
    private val chatUserRepository: ChatUserRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val userService: UserService,
    private val userRepository: UserRepository
) : ChatService {

    @Transactional
    override fun createGroup(request: ChatRequestDTO): ChatResponseDTO {
        val owner = userService.getCurrentUser()
        request.apply { this.isGroup = true }
        request.title ?: throw TitleNullException()
        val chat = chatRepository.save(request.toEntity())

        val chatUser = ChatUser(
            joinedDate = Instant.now(),
            isOwner = true,
            chat = chat,
            user = owner
        )
        chatUserRepository.save(chatUser)
        val response = chat.toDTO()
        messagingTemplate.convertAndSendToUser(owner.telegramId, "/queue/messages", chatResponseDTO(response))
        return response
    }

    override fun addMemberToGroup(request: ChatUserRequestDTO) {
        val currentUser = userService.getCurrentUser()
        val foundUser =
            userRepository.findAllByIdInAndDeletedFalse(request.userIds.filterNot { currentUser.id == it }.toSet())
                .map { it }
                .toSet()
                .let { foundUser ->
                    val missingIds = request.userIds.toSet() - foundUser.map { it.id }.toSet()
                    if (missingIds.isNotEmpty()) {
                        throw ObjectIdsNullException(missingIds)
                    }
                    foundUser
                }
        val chat = chatRepository.findByIdAndDeletedFalse(request.chatId) ?: throw ChatNotFoundException(request.chatId)
        chatRepository.isUserOwner(currentUser.id!!)
            .runIfFalse { throw UserIsNotChatOwnerException(currentUser.firstName) }
        val chatUsers = foundUser.map { request.toEntity(chat, user = it) }
        val saved = chatUserRepository.saveAll(chatUsers)
        saved.forEach { chatUser ->
            messagingTemplate.convertAndSendToUser(chatUser.user.telegramId, "/queue/messages", chatUser)
        }
    }

    //    @Transactional
//    override fun getOrCreateDirectChat(request: ChatRequestDtoForUsers): ChatResponseDtoForUsers {
//        val telegramId = getTelegramId()
//        val owner = userRepository.findByTelegramIdAndDeletedFalse(telegramId)
//            ?: throw UserNotFoundException()
//
//        val target = userRepository.findByUsernameAndDeletedFalse(request.username)
//            ?: throw UserNotFoundException()
//
//        if (owner.id == target.id) throw UserSendMessageConflictException(owner.id!!)
//
//
//        var chat = chatRepository.findDirectChat(owner.id!!, target.id!!)
//            ?: chatRepository.findDirectChat(target.id!!, owner.id!!)
//
//        if (chat == null) {
//
//            chat = chatRepository.save(Chat(title = null, isGroup = false))
//
//            listOf(
//                owner to true,
//                target to false
//            ).forEach { (user, isOwner) ->
//                chatUserRepository.save(
//                    ChatUser(
//                        chat = chat,
//                        user = user,
//                        isOwner = isOwner,
//                        joinedDate = Instant.now()
//                    )
//                )
//            }
//        }
//
//        val participants = chatUserRepository.findUsersByChatId(chat.id!!).mapNotNull { it.username }
//
//        return ChatResponseDtoForUsers(
//            id = chat.id!!,
//            participants = participants,
//            createdDate = chat.createdDate ?: Instant.now()
//        )
//    }
//    override fun getMyChats(): List<ChatResponseDtoForUsers> =
//        userService.getCurrentUser()
//            .let { user ->
//                chatUserRepository.findAllByUserIdAndDeletedFalse(user.id!!)
//                    .map { cu ->
//                        val chat = cu.chat
//                        val participants = chatUserRepository.findUsersByChatIdAndDeletedFalse(chat.id!!)
//                        ChatResponseDtoForUsers(
//                            id = chat.id!!,
//                            participants = participants.resolveNames(),
//                            isGroup = chat.isGroup,
//                            createdDate = chat.createdDate!!,
//                            name = if (chat.isGroup) chat.title
//                            else participants.first { it.id != cu.user.id }.resolveName()
//                        )
//                    }.also { response ->
//                        messagingTemplate.convertAndSendToUser(user.telegramId, "/queue/messages", response)
//                    }
//            }

    @Transactional(readOnly = true)
    override fun getChats(pageable: Pageable): Page<ChatsWithNew> {
        val user = userService.getCurrentUser()
        val allChats = chatRepository.getAllChats(user.id!!, pageable)
        messagingTemplate.convertAndSendToUser(user.telegramId, "/queue/messages", allChats.content)
        return allChats
    }

    private fun chatResponseDTO(response: ChatResponseDTO) = response

    private fun User.resolveName(): String =
        username ?: listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { "Unknown" }

    private fun List<User>.resolveNames(): List<String> = map { it.resolveName() }

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
    private val appFileRepository: AppFileRepository,
    private val userService: UserService
//    @Value("\${file.attachments.path}") private val attachmentsDir: String
) : MessageService {

    @Transactional
    override fun sendMessage(request: MessageRequestDTO, fileHash: String?) {
        val sender = userService.getCurrentUser()
        val (chat, target) = resolveChat(request, sender)
        checkMembership(chat.id!!, sender.id!!)
        val replyTo = resolveReply(request.replyToId, chat.id!!)
        val msg = messageRepository.save(createMessage(chat, sender, target, replyTo, request))
        val attachments = buildAttachments(fileHash, msg)
        val users = chatUserRepository.findAllByChatIdAndDeletedFalse(chat.id!!)
        saveStatuses(msg, users, sender.id!!)
        sendToUsers(msg, attachments, users)
    }

    override fun getChatMessages(request: ChatMessagesRequestDto) {
        val chat = chatRepository.findById(request.chatId)
            .orElseThrow { ChatNotFoundException(request.chatId) }

        val currentUserId = userService.getCurrentUser().id!!
        val response = buildChatMessagesResponse(chat.id!!, request, currentUserId)

        messagingTemplate.convertAndSendToUser(getTelegramId(), "/queue/messages", response)
        messageStatusRepository
            .updateStatusForUserInChatExcludingSender(currentUserId, chat.id!!)
    }


    override fun deleteMessages(request: ChatMessagesRequestDto, deleteMessageIds: List<Long>) {
        val chat = chatRepository.findById(request.chatId)
            .orElseThrow { ChatNotFoundException(request.chatId) }

        val currentUserId = userService.getCurrentUser().id!!

        messageRepository.trashList(deleteMessageIds)
        val messageStatusIds = messageStatusRepository
            .findByMessageIdInAndDeletedFalse(deleteMessageIds)
            .map { it?.id!! }
        messageStatusRepository.trashList(messageStatusIds)

        if (request.beforeId != null && deleteMessageIds.contains(request.beforeId)) {
            throw BeforeIdIsDeletedException(request.beforeId)
        }

        val response = buildChatMessagesResponse(chat.id!!, request, currentUserId)

        messagingTemplate.convertAndSendToUser(getTelegramId(), "/queue/messages", response)
    }

    @Transactional
    override fun updateMessage(request: MessageUpdateRequestDTO) {
        val sender = userService.getCurrentUser()
        val message = messageRepository.findByIdAndDeletedFalse(request.messageId) ?: throw MessageNotFoundException(
            request.messageId
        )

        if (message.sender.id != sender.id) {
            throw ConflictMessageException()
        }
        val chat = chatRepository.findById(request.chatId)
            .orElseThrow { ChatNotFoundException(request.chatId) }
        if (message.chat.id != chat.id) {
            throw ChatConflictException(chat.id!!)
        }

        request.content?.let { message.content = it }
        request.caption?.let { message.caption = it }

        val updatedMessage = messageRepository.save(message)


        val statusList = messageStatusRepository.findAllByUserIdAndMessageChatId(sender.id!!, chat.id!!)
        val statusMap = statusList.associateBy { it.message.id!! }
        val status = statusMap[updatedMessage.id]?.status ?: Status.SENT


        val responseDto = updatedMessage.toDTO(status)

        val chatUsers = chatUserRepository.findAllByChatIdAndDeletedFalse(chat.id!!)
        chatUsers.forEach { cu ->
            messagingTemplate.convertAndSendToUser(cu.user.telegramId, "/queue/messages", responseDto)
        }
    }

    private fun resolveChat(request: MessageRequestDTO, sender: User): Pair<Chat, User?> =
        if (request.isGroup) {
            val c = chatRepository.findById(request.chatId ?: throw ObjectIdNullException())
                .orElseThrow { ChatNotFoundException() }
                .also { if (!it.isGroup) throw ChatIsNotGroupException(it.isGroup) }
            c to null
        } else {
            val t =
                userRepository.findByTelegramIdAndDeletedFalse(request.toTelegramId ?: throw ObjectIdNullException())
                    ?: throw UserNotFoundException("recipient ${request.toTelegramId} not found")
            findOrCreateDirectChat(sender, t) to t
        }

    private fun checkMembership(chatId: Long, userId: Long) {
        if (!chatUserRepository.existsByChatIdAndUserIdAndDeletedFalse(chatId, userId))
            throw NotChatMemberException()
    }

    private fun createMessage(
        chat: Chat,
        sender: User,
        target: User?,
        replyTo: Message?,
        request: MessageRequestDTO
    ) = Message(
        chat = chat,
        sender = sender,
        recipient = target,
        replyTo = replyTo,
        caption = request.caption,
        content = request.content
    )


    private fun resolveReply(replyToId: Long?, chatId: Long) =
        replyToId?.let {
            messageRepository.findById(it).orElseThrow { MessageNotFoundException(it) }
                .also { if (it.chat.id != chatId) throw ChatConflictException(chatId) }
        }

    private fun buildAttachments(fileHash: String?, msg: Message) =
        fileHash?.let { hash ->
            appFileRepository.findByCustomHashAndDeletedFalse(hash)?.let { f ->
                attachmentRepository.save(Attachment(f, msg)).let {
                    listOf(AttachmentInfo(it.id!!, f.customHash, f.mimeType, f.size, f.duration, f.height, f.width))
                }
            } ?: throw FileNotFoundException(hash)
        } ?: emptyList()

    private fun saveStatuses(msg: Message, users: List<ChatUser>, senderId: Long) =
        messageStatusRepository.saveAll(users.map {
            MessageStatus(
                msg,
                it.user,
                if (it.user.id == senderId) Status.READ else Status.SENT
            )
        })

    private fun sendToUsers(msg: Message, attachments: List<AttachmentInfo>, users: List<ChatUser>) {
        val dto = msg.toDTO(Status.SENT).copy(attachments = attachments)
        users.forEach { messagingTemplate.convertAndSendToUser(it.user.telegramId, "/queue/messages", dto) }
    }

    private fun buildChatMessagesResponse(
        chatId: Long,
        request: ChatMessagesRequestDto,
        currentUserId: Long
    ): ChatMessagesResponseDto {
        val startId = request.beforeId
            ?: messageRepository.findTopByChatIdOrderByIdDesc(chatId)?.id
            ?: Long.MAX_VALUE

        val messages = messageRepository.findByChatIdAndDeletedFalseAndIdLessThanOrderByIdDesc(
            chatId = chatId,
            id = startId,
            pageable = PageRequest.of(0, request.limit)
        )

        val statuses = messageStatusRepository.findAllByUserIdAndMessageChatId(currentUserId, chatId)
            .associateBy { it.message.id!! }

        val dtoList = messages.map { msg ->
            val status = statuses[msg.id]?.status ?: Status.SENT
            msg.toDTO(status)
        }

        val nextBeforeId = dtoList.lastOrNull()?.id
        val hasMore = dtoList.size == request.limit

        return ChatMessagesResponseDto(dtoList, nextBeforeId, hasMore)
    }

    private fun findOrCreateDirectChat(sender: User, target: User): Chat {
        if (sender.id == target.id) throw UserSendMessageConflictException(sender.id!!)
        val existing = chatRepository.findDirectChat(sender.id!!, target.id!!)
        if (existing != null) return existing
        val chat = chatRepository.save(Chat(title = null, isGroup = false))
        listOf(sender to true, target to false).forEach { (user, isOwner) ->
            chatUserRepository.save(ChatUser(joinedDate = Instant.now(), isOwner = isOwner, chat = chat, user = user))
        }
        return chat
    }
}

@Service
class FileServiceImpl(
    private val appFileRepository: AppFileRepository,
    private val fileUtils: FileUtils,
    private val userService: UserService
) : FileService {

    @Transactional
    override fun upload(file: MultipartFile, ownerId: Long, dir: String, messageType: MessageType): AppFile {
        if (file.isEmpty) throw IllegalArgumentException("File is empty!")

        val bytes = file.bytes
        val sha256 = fileUtils.calculateSHA256(bytes)

        appFileRepository.findBySha256Hash(sha256)?.let { return it }

        val (uniqueFileName, path) = writeFile(dir, sha256, bytes)

        val customHash = fileUtils.generateObfuscatedFileName(uniqueFileName)
        val mimeType = file.contentType ?: "application/octet-stream"

        val (duration, height, width) = extractMetadata(path, mimeType)

        val appFile = AppFile(
            ownerId = ownerId,
            filePath = path.toAbsolutePath().toString(),
            sha256Hash = sha256,
            customHash = customHash,
            mimeType = mimeType,
            type = messageType,
            size = file.size,
            duration = duration,
            height = height,
            width = width
        )

        return appFileRepository.save(appFile)
    }

    override fun uploadFile(file: MultipartFile): AttachmentInfo {
        val user = userService.getCurrentUser()
        val msgType = when {
            file.contentType?.startsWith("image/") == true -> MessageType.PHOTO
            file.contentType?.startsWith("video/") == true -> MessageType.VIDEO
            file.contentType?.startsWith("audio/") == true -> MessageType.AUDIO
            else -> MessageType.UNKNOWN
        }

        val appFile = upload(file, user.id!!, "uploads", msgType)

        return AttachmentInfo(
            id = null,
            customHash = appFile.customHash,
            type = appFile.mimeType,
            size = appFile.size,
            duration = appFile.duration,
            height = appFile.height,
            width = appFile.width
        )
    }

    private fun writeFile(
        dir: String,
        sha256: String,
        bytes: ByteArray
    ): Pair<String, Path> {
        getOrCreateDir(dir)
        val uniqueFileName = getUniqueFileName(sha256)
        val path = Paths.get(dir, uniqueFileName)
        Files.write(path, bytes)
        return Pair(uniqueFileName, path)
    }

    private fun getUniqueFileName(sha256: String): String {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
        return "$sha256-$timestamp"
    }

    private fun getOrCreateDir(dir: String) {
        File(dir).apply { if (!exists()) mkdirs() }
    }

    private fun extractMetadata(path: Path, mimeType: String): Triple<Int?, Int?, Int?> {
        return when {
            mimeType.startsWith("image/") -> {
                val img = ImageIO.read(path.toFile())
                val width = img?.width
                val height = img?.height
                Triple(null, height, width)
            }

            mimeType.startsWith("audio/") || mimeType.startsWith("video/") -> {
                try {
                    val metadata = com.drew.imaging.ImageMetadataReader.readMetadata(path.toFile())
                    val duration = metadata.directories
                        .flatMap { it.tags }
                        .firstOrNull { it.tagName.contains("Duration", ignoreCase = true) }
                        ?.description
                        ?.let { parseDuration(it) }

                    val width = metadata.directories
                        .flatMap { it.tags }
                        .firstOrNull { it.tagName.equals("Width", ignoreCase = true) }
                        ?.description?.replace("[^0-9]".toRegex(), "")?.toIntOrNull()

                    val height = metadata.directories
                        .flatMap { it.tags }
                        .firstOrNull { it.tagName.equals("Height", ignoreCase = true) }
                        ?.description?.replace("[^0-9]".toRegex(), "")?.toIntOrNull()

                    Triple(duration, height, width)
                } catch (ex: Exception) {
                    Triple(null, null, null)
                }
            }

            else -> Triple(null, null, null)
        }
    }


    private fun parseDuration(desc: String): Int? {
        val parts = desc.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> null
        }
    }

}
